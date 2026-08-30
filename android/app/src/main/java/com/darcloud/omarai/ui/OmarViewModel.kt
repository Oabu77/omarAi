package com.darcloud.omarai.ui

import android.app.Application
import android.app.Activity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darcloud.omarai.BuildConfig
import com.darcloud.omarai.OmarAiApplication
import com.darcloud.omarai.data.api.ApiResult
import com.darcloud.omarai.data.local.BusinessMetricsCalculator
import com.darcloud.omarai.data.local.BusinessState
import com.darcloud.omarai.data.local.ChatMessageEntity
import com.darcloud.omarai.data.local.ExportSnapshot
import com.darcloud.omarai.data.local.InvoiceStatuses
import com.darcloud.omarai.data.local.JobStatuses
import com.darcloud.omarai.data.local.LeadStatuses
import com.darcloud.omarai.data.local.PendingAttachment
import com.darcloud.omarai.data.local.TaskEntity
import com.darcloud.omarai.data.billing.PlayProduct
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class OmarViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as OmarAiApplication).container
    private val repository = container.repository

    val onboardingComplete: StateFlow<Boolean?> = container.preferences.onboardingComplete
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val messages: StateFlow<List<ChatMessageEntity>> = repository.messages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tasks: StateFlow<List<TaskEntity>> = repository.tasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val business: StateFlow<BusinessState> = repository.businessState
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            BusinessState(emptyList(), emptyList(), emptyList(), emptyList(), BusinessMetricsCalculator.calculate(emptyList(), emptyList(), emptyList())),
        )
    val billing = container.billingManager.state
    val apiConfigured: Boolean = container.apiClient.isConfigured
    val authenticatedSessionAvailable: Boolean
        get() = container.apiClient.hasAuthenticatedSession

    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableFeedback = MutableStateFlow<String?>(null)
    val feedback = mutableFeedback.asStateFlow()
    private val mutableRemoteIntegrations = MutableStateFlow<List<RemoteIntegrationUi>>(emptyList())
    val remoteIntegrations = mutableRemoteIntegrations.asStateFlow()

    fun finishOnboarding() = viewModelScope.launch {
        container.preferences.setOnboardingComplete(true)
    }

    fun submit(text: String, attachments: List<PendingAttachment>, permissionsUsed: Set<String>) = runAction {
        repository.submitRequest(text, attachments, permissionsUsed)
        "Request routed. Check the conversation and Command Center for the exact state."
    }

    fun addCustomer(name: String, phone: String?, email: String?) = runAction {
        repository.addCustomer(name, phone, email)
        "Customer saved on this device."
    }

    fun addLead(title: String, amountMinor: Long?) = runAction {
        repository.addLead(title, amountMinor)
        "Lead saved on this device."
    }

    fun updateLeadStatus(id: String, status: String) = runAction {
        repository.updateLeadStatus(id, status)
        "Lead status updated locally."
    }

    fun addJob(title: String, status: String, scheduledAtEpochMs: Long?) = runAction {
        repository.addJob(title, status, scheduledAtEpochMs)
        "Job saved on this device."
    }

    fun addInvoice(label: String, totalMinor: Long, paidMinor: Long, status: String) = runAction {
        repository.addInvoice(label, totalMinor, paidMinor, status)
        "Invoice saved on this device; no invoice was sent and no payment was processed."
    }

    fun approve(taskId: String) = runAction { repository.approveTask(taskId) }
    fun cancel(taskId: String) = runAction { repository.cancelTask(taskId) }
    fun refreshTask(taskId: String) = runAction { repository.refreshRemoteTask(taskId) }

    fun report(message: ChatMessageEntity, category: String, note: String?) = runAction {
        repository.reportOutput(message, category, note)
    }

    fun refreshIntegrations() = runAction {
        when (val response = container.apiClient.call { integrations() }) {
            is ApiResult.Success -> {
                mutableRemoteIntegrations.value = response.value.integrations.map { (id, state) ->
                    RemoteIntegrationUi(
                        displayName = id.replace(Regex("([a-z])([A-Z])"), "$1 $2")
                            .replaceFirstChar { it.uppercase() },
                        state = when (state.status) {
                            "CONNECTED" -> if (state.mode == "production") "CONNECTED_PRODUCTION" else "CONNECTED_TEST"
                            "PENDING", "DISCONNECTED", "DEGRADED", "FAILED" -> state.status
                            else -> "FAILED"
                        },
                        detail = state.evidence,
                    )
                }
                "Integration states refreshed from the service."
            }
            is ApiResult.Failure -> response.userMessage
        }
    }

    fun launchPurchase(activity: Activity, product: PlayProduct) {
        container.billingManager.launchPurchase(activity, product)?.let { mutableFeedback.value = it }
    }

    fun restorePurchases() {
        container.billingManager.restorePurchases()
    }

    suspend fun exportJson(): String = exportToJson(repository.exportSnapshot()).toString(2)

    fun deleteLocalData() = runAction {
        repository.deleteLocalData()
        container.preferences.clear()
        "Local records, conversations, tasks, reports, audit events, and app preferences were deleted from this device."
    }

    fun requestRemoteDeletion() = runAction {
        when (val response = repository.requestRemoteAccountDeletion()) {
            is ApiResult.Success -> {
                val value = response.value
                if (value.status in setOf("ACCEPTED", "SCHEDULED", "COMPLETED")) {
                    "Deletion confirmed. Reference: ${value.deletionId}. Status: ${value.status}."
                } else {
                    "The service did not confirm deletion. Request: ${response.requestId}; status: ${value.status}."
                }
            }
            is ApiResult.Failure -> "Remote deletion was not confirmed: ${response.userMessage}"
        }
    }

    fun clearFeedback() { mutableFeedback.value = null }

    private fun runAction(block: suspend () -> String) {
        if (mutableBusy.value) return
        viewModelScope.launch {
            mutableBusy.value = true
            mutableFeedback.value = try {
                block()
            } catch (error: IllegalArgumentException) {
                error.message ?: "Please check the entered information."
            } catch (_: Exception) {
                "The local action failed and was not reported as complete."
            }
            mutableBusy.value = false
        }
    }

    private fun exportToJson(value: ExportSnapshot): JSONObject = JSONObject().apply {
        put("format", "omar-ai-local-export-v1")
        put("exportedAtEpochMs", System.currentTimeMillis())
        put("package", BuildConfig.APPLICATION_ID)
        put("customers", JSONArray().apply { value.customers.forEach { item ->
            put(JSONObject().apply {
                put("id", item.id); put("name", item.name); putNullable("phone", item.phone)
                putNullable("email", item.email); put("createdAtEpochMs", item.createdAtEpochMs)
            })
        } })
        put("leads", JSONArray().apply { value.leads.forEach { item ->
            put(JSONObject().apply {
                put("id", item.id); put("title", item.title); put("status", item.status)
                putNullable("estimatedValueMinor", item.estimatedValueMinor); put("currencyCode", item.currencyCode)
                put("createdAtEpochMs", item.createdAtEpochMs)
            })
        } })
        put("jobs", JSONArray().apply { value.jobs.forEach { item ->
            put(JSONObject().apply {
                put("id", item.id); put("title", item.title); put("status", item.status)
                putNullable("scheduledAtEpochMs", item.scheduledAtEpochMs)
                putNullable("completedAtEpochMs", item.completedAtEpochMs); put("createdAtEpochMs", item.createdAtEpochMs)
            })
        } })
        put("invoices", JSONArray().apply { value.invoices.forEach { item ->
            put(JSONObject().apply {
                put("id", item.id); put("label", item.label); put("status", item.status)
                put("totalMinor", item.totalMinor); put("paidMinor", item.paidMinor)
                put("currencyCode", item.currencyCode); put("createdAtEpochMs", item.createdAtEpochMs)
            })
        } })
        put("tasks", JSONArray().apply { value.tasks.forEach { item ->
            put(JSONObject().apply {
                put("id", item.id); putNullable("remoteId", item.remoteId); put("title", item.title)
                put("agent", item.agent); put("status", item.status); put("startedAtEpochMs", item.startedAtEpochMs)
                put("updatedAtEpochMs", item.updatedAtEpochMs); put("actionsPerformed", item.actionsPerformed)
                put("permissionsUsed", item.permissionsUsed); putNullable("result", item.result)
                putNullable("error", item.error); putNullable("providerEvidenceId", item.providerEvidenceId)
            })
        } })
        put("auditEvents", JSONArray().apply { value.auditEvents.forEach { item ->
            put(JSONObject().apply {
                put("id", item.id); putNullable("taskId", item.taskId); put("eventType", item.eventType)
                put("detail", item.detail); put("createdAtEpochMs", item.createdAtEpochMs)
            })
        } })
        put("messages", JSONArray().apply { value.messages.forEach { item ->
            put(JSONObject().apply {
                put("id", item.id); put("role", item.role); put("text", item.text)
                putNullable("agent", item.agent); putNullable("taskId", item.taskId)
                put("createdAtEpochMs", item.createdAtEpochMs)
            })
        } })
        put("outputReports", JSONArray().apply { value.outputReports.forEach { item ->
            put(JSONObject().apply {
                put("id", item.id); put("messageId", item.messageId); put("category", item.category)
                putNullable("note", item.note); put("status", item.status)
                putNullable("remoteReportId", item.remoteReportId); put("createdAtEpochMs", item.createdAtEpochMs)
            })
        } })
    }

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }

    companion object {
        val leadStatuses = listOf(
            LeadStatuses.NEW, LeadStatuses.CONTACTED, LeadStatuses.QUALIFIED,
            LeadStatuses.WON, LeadStatuses.LOST,
        )
        val jobStatuses = listOf(JobStatuses.SCHEDULED, JobStatuses.COMPLETED, JobStatuses.CANCELLED)
        val invoiceStatuses = listOf(
            InvoiceStatuses.DRAFT, InvoiceStatuses.SENT, InvoiceStatuses.PARTIAL,
            InvoiceStatuses.PAID, InvoiceStatuses.VOID,
        )
    }
}

data class RemoteIntegrationUi(val displayName: String, val state: String, val detail: String)
