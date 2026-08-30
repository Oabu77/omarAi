package com.darcloud.omarai.data.local

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.darcloud.omarai.data.api.AccountDeletionData
import com.darcloud.omarai.data.api.AiOutputReportRequest
import com.darcloud.omarai.data.api.ApiResult
import com.darcloud.omarai.data.api.ApproveTaskRequest
import com.darcloud.omarai.data.api.AttachmentMetadata
import com.darcloud.omarai.data.api.CancelTaskRequest
import com.darcloud.omarai.data.api.DeleteAccountRequest
import com.darcloud.omarai.data.api.OmarApiClient
import com.darcloud.omarai.data.api.PlanTaskData
import com.darcloud.omarai.data.api.PlanTaskRequest
import com.darcloud.omarai.data.api.TaskDetailsData
import com.darcloud.omarai.data.api.TaskReceipt
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PendingAttachment(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val source: String,
)

data class BusinessState(
    val customers: List<CustomerEntity>,
    val leads: List<LeadEntity>,
    val jobs: List<JobEntity>,
    val invoices: List<InvoiceEntity>,
    val metrics: BusinessMetrics,
)

data class ExportSnapshot(
    val customers: List<CustomerEntity>,
    val leads: List<LeadEntity>,
    val jobs: List<JobEntity>,
    val invoices: List<InvoiceEntity>,
    val tasks: List<TaskEntity>,
    val auditEvents: List<AuditEventEntity>,
    val messages: List<ChatMessageEntity>,
    val outputReports: List<AiOutputReportEntity>,
)

class OmarRepository(
    private val context: Context,
    private val database: OmarDatabase,
    private val apiClient: OmarApiClient,
    private val preferences: UserPreferences,
) {
    private val dao = database.omarDao()

    val tasks: Flow<List<TaskEntity>> = dao.observeTasks()
    val messages: Flow<List<ChatMessageEntity>> = dao.observeMessages()
    val businessState: Flow<BusinessState> = combine(
        dao.observeCustomers(),
        dao.observeLeads(),
        dao.observeJobs(),
        dao.observeInvoices(),
    ) { customers, leads, jobs, invoices ->
        BusinessState(
            customers,
            leads,
            jobs,
            invoices,
            BusinessMetricsCalculator.calculate(leads, jobs, invoices),
        )
    }

    suspend fun addCustomer(name: String, phone: String?, email: String?) {
        require(name.isNotBlank())
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        dao.insertCustomer(CustomerEntity(id, name.trim(), phone.clean(), email.clean(), now))
        dao.insertAuditEvent(audit(null, "CUSTOMER_CREATED", "Local customer record $id created"))
    }

    suspend fun addLead(title: String, estimatedValueMinor: Long?) {
        require(title.isNotBlank())
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        dao.insertLead(
            LeadEntity(id, null, title.trim(), null, LeadStatuses.NEW, estimatedValueMinor, "USD", now),
        )
        dao.insertAuditEvent(audit(null, "LEAD_CREATED", "Local lead record $id created"))
    }

    suspend fun updateLeadStatus(id: String, status: String) {
        require(status in setOf(
            LeadStatuses.NEW, LeadStatuses.CONTACTED, LeadStatuses.QUALIFIED,
            LeadStatuses.WON, LeadStatuses.LOST,
        ))
        dao.updateLeadStatus(id, status)
        dao.insertAuditEvent(audit(null, "LEAD_STATUS_CHANGED", "Local lead $id changed to $status"))
    }

    suspend fun addJob(title: String, status: String, scheduledAtEpochMs: Long?) {
        require(title.isNotBlank())
        require(status in setOf(JobStatuses.SCHEDULED, JobStatuses.COMPLETED, JobStatuses.CANCELLED))
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        dao.insertJob(
            JobEntity(
                id = id,
                customerId = null,
                title = title.trim(),
                status = status,
                scheduledAtEpochMs = scheduledAtEpochMs,
                completedAtEpochMs = now.takeIf { status == JobStatuses.COMPLETED },
                quotedMinor = null,
                currencyCode = "USD",
                createdAtEpochMs = now,
            ),
        )
        dao.insertAuditEvent(audit(null, "JOB_CREATED", "Local job record $id created with status $status"))
    }

    suspend fun addInvoice(label: String, totalMinor: Long, paidMinor: Long, status: String) {
        require(label.isNotBlank())
        require(totalMinor >= 0L && paidMinor in 0L..totalMinor)
        require(status in setOf(
            InvoiceStatuses.DRAFT, InvoiceStatuses.SENT, InvoiceStatuses.PARTIAL,
            InvoiceStatuses.PAID, InvoiceStatuses.VOID,
        ))
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        dao.insertInvoice(
            InvoiceEntity(
                id = id,
                customerId = null,
                label = label.trim(),
                status = status,
                totalMinor = totalMinor,
                paidMinor = paidMinor,
                currencyCode = "USD",
                createdAtEpochMs = now,
                paymentReceivedAtEpochMs = now.takeIf { paidMinor > 0L },
            ),
        )
        dao.insertAuditEvent(audit(null, "INVOICE_CREATED", "Local invoice record $id created with status $status"))
    }

    suspend fun submitRequest(
        text: String,
        attachments: List<PendingAttachment>,
        permissionsUsed: Set<String>,
    ) {
        if (text.isBlank() && attachments.isEmpty()) return
        val requestText = text.ifBlank { "Analyze the selected attachment." }.trim()
        val now = System.currentTimeMillis()
        dao.insertMessage(
            ChatMessageEntity(UUID.randomUUID().toString(), "USER", requestText, null, null, now, false),
        )
        val route = IntentRouter.route(requestText, attachments.any { it.mimeType.startsWith("image/") })

        if (!route.supportedInV1) {
            dao.insertMessage(
                ChatMessageEntity(
                    UUID.randomUUID().toString(),
                    "ASSISTANT",
                    "${route.agent}: ${route.explanation}",
                    route.agent,
                    null,
                    now + 1,
                    true,
                ),
            )
            return
        }

        if (!apiClient.isConfigured) {
            val taskId = UUID.randomUUID().toString()
            dao.insertTask(
                TaskEntity(
                    id = taskId,
                    remoteId = null,
                    title = requestText.take(80),
                    agent = route.agent,
                    status = TaskStatus.PLANNED.name,
                    startedAtEpochMs = now,
                    updatedAtEpochMs = now,
                    actionsPerformed = "Request saved locally",
                    permissionsUsed = permissionsUsed.sorted().joinToString(),
                    result = null,
                    error = "Omar AI service is not connected; no analysis or external action occurred.",
                    cancellable = true,
                    approvalPrompt = null,
                    providerEvidenceId = null,
                ),
            )
            val caution = if (route.capability == ProductCapability.ESTIMATING) {
                " Photo estimates are preliminary and are not guaranteed professional assessments."
            } else ""
            dao.insertMessage(
                ChatMessageEntity(
                    UUID.randomUUID().toString(),
                    "ASSISTANT",
                    "I routed this to ${route.agent} and saved it as Planned, but the Omar AI service is not connected. No analysis or external action occurred.$caution",
                    route.agent,
                    taskId,
                    now + 1,
                    true,
                ),
            )
            dao.insertAuditEvent(audit(taskId, "REQUEST_SAVED", "Saved locally; remote session unavailable"))
            return
        }

        val clientRequestId = UUID.randomUUID().toString()
        val attachmentMetadata = attachments.map {
            AttachmentMetadata(it.displayName.take(120), it.mimeType.take(120), it.source.take(40))
        }.takeIf { it.isNotEmpty() }
        when (val planned = apiClient.call {
            planTask(
                clientRequestId,
                PlanTaskRequest(
                    text = requestText,
                    locale = Locale.getDefault().toLanguageTag(),
                    conversationId = preferences.activeConversationId(),
                    attachments = attachmentMetadata,
                ),
            )
        }) {
            is ApiResult.Success -> saveRemoteTask(planned.value, requestText, permissionsUsed, now)
            is ApiResult.Failure -> saveFailedRequest(requestText, route.agent, permissionsUsed, planned.userMessage)
        }
    }

    suspend fun approveTask(taskId: String): String {
        val local = dao.getTask(taskId) ?: return "Task no longer exists."
        if (local.status != TaskStatus.AWAITING_APPROVAL.name) return "This task is not waiting for approval."
        val remoteId = local.remoteId ?: return "This local task has no remote action to approve."
        val idempotencyKey = local.approvalIdempotencyKey ?: UUID.randomUUID().toString()
        val pending = local.copy(approvalIdempotencyKey = idempotencyKey)
        if (local.approvalIdempotencyKey == null) dao.updateTask(pending)
        return when (val result = apiClient.call {
            approveTask(remoteId, idempotencyKey, ApproveTaskRequest(approved = true))
        }) {
            is ApiResult.Success -> {
                updateFromReceipt(pending, result.value.taskReceipt)
                dao.insertAuditEvent(audit(taskId, "APPROVAL_CONFIRMED", "Approval response received from service"))
                "Approval confirmed by the Omar AI service."
            }
            is ApiResult.Failure -> {
                dao.updateTask(pending.copy(error = result.userMessage, updatedAtEpochMs = System.currentTimeMillis()))
                dao.insertAuditEvent(audit(taskId, "APPROVAL_UNVERIFIED", result.userMessage))
                "Approval could not be verified. The task remains waiting."
            }
        }
    }

    suspend fun cancelTask(taskId: String): String {
        val local = dao.getTask(taskId) ?: return "Task no longer exists."
        val status = TaskStatus.entries.firstOrNull { it.name == local.status } ?: TaskStatus.FAILED
        if (status.isTerminal || !local.cancellable) return "This task can no longer be cancelled."
        val remoteId = local.remoteId
        if (remoteId == null) {
            dao.updateTask(
                local.copy(
                    status = TaskStatus.CANCELLED.name,
                    updatedAtEpochMs = System.currentTimeMillis(),
                    actionsPerformed = listOf(local.actionsPerformed, "Local task cancelled").filter { it.isNotBlank() }.joinToString("\n"),
                    result = "Cancelled locally; no external action was active.",
                    error = null,
                    cancellable = false,
                ),
            )
            dao.insertAuditEvent(audit(taskId, "TASK_CANCELLED", "Local cancellation completed"))
            return "Cancelled locally."
        }
        val idempotencyKey = local.cancellationIdempotencyKey ?: UUID.randomUUID().toString()
        val pending = local.copy(cancellationIdempotencyKey = idempotencyKey)
        if (local.cancellationIdempotencyKey == null) dao.updateTask(pending)
        return when (val result = apiClient.call {
            cancelTask(remoteId, idempotencyKey, CancelTaskRequest())
        }) {
            is ApiResult.Success -> {
                updateFromReceipt(pending, result.value.taskReceipt)
                dao.insertAuditEvent(audit(taskId, "CANCELLATION_CONFIRMED", "Service returned task state"))
                "Cancellation response verified."
            }
            is ApiResult.Failure -> {
                dao.updateTask(pending.copy(error = result.userMessage, updatedAtEpochMs = System.currentTimeMillis()))
                dao.insertAuditEvent(audit(taskId, "CANCELLATION_UNVERIFIED", result.userMessage))
                "Cancellation could not be verified; the previous task state is unchanged."
            }
        }
    }

    suspend fun reportOutput(message: ChatMessageEntity, category: String, note: String?): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val local = AiOutputReportEntity(id, message.id, category, note.clean(), "SAVED_LOCAL", null, now)
        dao.insertOutputReport(local)
        if (!apiClient.isConfigured) return "Report saved on this device. It was not submitted because the service is disconnected."
        val task = message.taskId?.let { dao.getTask(it) }
        val remoteTaskId = task?.remoteId
            ?: return "Report saved on this device. This output has no remote task to receive the report."
        val reportText = buildString {
            append("User flagged Android output for moderation.")
            note.clean()?.let { append("\nUser note: ").append(it) }
            append("\nOutput:\n").append(message.text)
        }
        return when (val result = apiClient.call {
            reportAiOutput(
                id,
                AiOutputReportRequest(
                    taskId = remoteTaskId,
                    provider = "android-user-report",
                    output = reportText,
                    reportedState = "prepared",
                    category = category,
                ),
            )
        }) {
            is ApiResult.Success -> {
                val accepted = result.value.acceptedState == "prepared" && !result.value.taskTransitionApplied
                dao.insertOutputReport(
                    local.copy(
                        status = if (accepted) "SUBMITTED" else "SAVED_LOCAL",
                        remoteReportId = result.value.reportId.takeIf { accepted },
                    ),
                )
                if (accepted) "Report submitted for moderation. Reference: ${result.value.reportId}" else "Report saved locally; the service did not confirm moderation acceptance."
            }
            is ApiResult.Failure -> "Report saved locally, but submission failed: ${result.userMessage}"
        }
    }

    suspend fun exportSnapshot(): ExportSnapshot = ExportSnapshot(
        dao.allCustomers(), dao.allLeads(), dao.allJobs(), dao.allInvoices(), dao.allTasks(),
        dao.allAuditEvents(), dao.allMessages(), dao.allOutputReports(),
    )

    suspend fun deleteLocalData() {
        withContext(Dispatchers.IO) {
            database.clearAllTables()
            context.cacheDir.listFiles()
                .orEmpty()
                .filter { it.isFile && it.name.startsWith("camera-") }
                .forEach { it.delete() }
            context.contentResolver.persistedUriPermissions.forEach { grant ->
                val flags = (if (grant.isReadPermission) android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                    (if (grant.isWritePermission) android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
                if (flags != 0) runCatching {
                    context.contentResolver.releasePersistableUriPermission(grant.uri, flags)
                }
            }
        }
    }

    suspend fun startNewConversation() {
        preferences.startNewConversation()
        dao.clearMessages()
    }

    suspend fun requestRemoteAccountDeletion(): ApiResult<AccountDeletionData> = apiClient.call {
        deleteAccount(
            UUID.randomUUID().toString(),
            "DELETE OMAR AI ACCOUNT",
            DeleteAccountRequest(confirm = true),
        )
    }

    suspend fun refreshRemoteTask(taskId: String): String {
        val local = dao.getTask(taskId) ?: return "Task no longer exists."
        val remoteId = local.remoteId ?: return "This task exists only on this device."
        return when (val result = apiClient.call { getTask(remoteId) }) {
            is ApiResult.Success -> {
                updateFromDetails(local, result.value)
                "Task state refreshed from the service."
            }
            is ApiResult.Failure -> "Refresh failed: ${result.userMessage}"
        }
    }

    private suspend fun saveRemoteTask(
        remote: PlanTaskData,
        requestText: String,
        permissionsUsed: Set<String>,
        now: Long,
    ) {
        val localId = UUID.randomUUID().toString()
        val safeStatus = mapBackendTaskStatus(remote.taskReceipt)
        val agents = remote.plan.steps.map { it.agent }.filter { it.isNotBlank() }.distinct()
        val agentLabel = agents.joinToString().ifBlank { "Omar Core Agent" }
        val plannedActions = remote.plan.steps.joinToString("\n") { "${it.ordinal + 1}. ${it.action}" }
        val task = TaskEntity(
            id = localId,
            remoteId = remote.taskReceipt.taskId,
            title = remote.plan.title.ifBlank { requestText.take(80) },
            agent = agentLabel,
            status = safeStatus.name,
            startedAtEpochMs = now,
            updatedAtEpochMs = System.currentTimeMillis(),
            actionsPerformed = "None — the plan was prepared only",
            permissionsUsed = permissionsUsed.sorted().joinToString(),
            result = remote.reply,
            error = null,
            cancellable = remote.taskReceipt.cancellable && !safeStatus.isTerminal,
            approvalPrompt = plannedActions.takeIf { remote.taskReceipt.approvalRequired },
            providerEvidenceId = remote.taskReceipt.providerEvidence.referenceId,
        )
        dao.insertTask(task)
        val estimateWarning = if (task.agent == "Estimating Agent") {
            " Estimates are preliminary ranges, not guaranteed professional assessments."
        } else ""
        dao.insertMessage(
            ChatMessageEntity(
                UUID.randomUUID().toString(), "ASSISTANT",
                "${remote.reply}\n\n${userFacingState(safeStatus)}$estimateWarning",
                task.agent, localId, now + 1, true,
            ),
        )
        dao.insertAuditEvent(audit(localId, "TASK_RECEIVED", "Remote task ${remote.taskReceipt.taskId}; state ${safeStatus.name}"))
    }

    private suspend fun saveFailedRequest(
        requestText: String,
        agent: String,
        permissionsUsed: Set<String>,
        error: String,
    ) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        dao.insertTask(
            TaskEntity(
                id, null, requestText.take(80), agent, TaskStatus.FAILED.name, now, now,
                "No verified action completed", permissionsUsed.sorted().joinToString(), null, error,
                false, null, null,
            ),
        )
        dao.insertMessage(
            ChatMessageEntity(
                UUID.randomUUID().toString(), "ASSISTANT",
                "I could not complete this request: $error", agent, id, now + 1, true,
            ),
        )
        dao.insertAuditEvent(audit(id, "TASK_FAILED", error))
    }

    private suspend fun updateFromReceipt(local: TaskEntity, receipt: TaskReceipt) {
        val safeStatus = mapBackendTaskStatus(receipt)
        dao.updateTask(
            local.copy(
                remoteId = receipt.taskId,
                status = safeStatus.name,
                updatedAtEpochMs = System.currentTimeMillis(),
                error = null,
                cancellable = receipt.cancellable && !safeStatus.isTerminal,
                approvalPrompt = local.approvalPrompt.takeIf { receipt.approvalRequired },
                providerEvidenceId = receipt.providerEvidence.referenceId,
            ),
        )
    }

    private suspend fun updateFromDetails(local: TaskEntity, remote: TaskDetailsData) {
        val safeStatus = mapBackendTaskStatus(remote.task.receipt)
        val completed = remote.steps.filter { it.status == "completed" }
        val agents = remote.steps.map { it.agent }.filter { it.isNotBlank() }.distinct()
        val pendingApproval = remote.steps.filter { it.requiresApproval && it.status == "waiting_approval" }
            .joinToString("\n") { "${it.ordinal + 1}. ${it.action}" }
        dao.updateTask(
            local.copy(
                remoteId = remote.task.id,
                title = remote.task.title.ifBlank { local.title },
                agent = agents.joinToString().ifBlank { local.agent },
                status = safeStatus.name,
                updatedAtEpochMs = System.currentTimeMillis(),
                actionsPerformed = completed.joinToString("\n") { it.resultSummary ?: it.action }
                    .ifBlank { "None reported as completed" },
                error = remote.task.errorMessage,
                cancellable = remote.task.receipt.cancellable && !safeStatus.isTerminal,
                approvalPrompt = pendingApproval.takeIf { it.isNotBlank() },
                providerEvidenceId = remote.task.receipt.providerEvidence.referenceId,
            ),
        )
    }

    private fun userFacingState(status: TaskStatus): String = when (status) {
        TaskStatus.AWAITING_APPROVAL -> "A plan is ready and waiting for your approval."
        TaskStatus.COMPLETED -> "The service returned independently verified completion."
        TaskStatus.SUBMITTED -> "The request was submitted, but completion is not yet verified."
        TaskStatus.QUEUED -> "Approval was recorded and the task is queued; no external action is complete."
        TaskStatus.RUNNING -> "The task is running; completion is not yet verified."
        TaskStatus.VERIFYING -> "The request is being verified; it is not complete yet."
        TaskStatus.FAILED -> "The request failed and was not reported as complete."
        else -> "Task state: ${status.name.replace('_', ' ').lowercase()}."
    }

    private fun audit(taskId: String?, type: String, detail: String) = AuditEventEntity(
        UUID.randomUUID().toString(), taskId, type, detail.take(500), System.currentTimeMillis(),
    )

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        fun attachmentFromUri(resolver: ContentResolver, uri: Uri, source: String): PendingAttachment {
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            var name = "attachment"
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) name = cursor.getString(0) ?: name
            }
            return PendingAttachment(uri, name, mimeType, source)
        }
    }
}
