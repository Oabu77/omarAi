package com.darcloud.omarai.data.api

import com.squareup.moshi.Json

data class ApiEnvelope<T>(
    val ok: Boolean,
    val data: T? = null,
    val requestId: String,
    val error: ApiErrorBody? = null,
)

data class ApiFailureEnvelope(
    val ok: Boolean,
    val error: ApiErrorBody? = null,
    val requestId: String? = null,
)

data class ApiErrorBody(val code: String, val message: String)

data class HealthData(
    val service: String,
    val version: String,
    val status: String,
    val checkedAt: String,
    val coreReady: Boolean,
    val integrations: Map<String, IntegrationWireState>,
)

data class AttachmentMetadata(val name: String, val mimeType: String, val source: String)

data class PlanTaskRequest(
    val text: String,
    val locale: String,
    val conversationId: String,
    /** Presence intentionally causes FILE_SERVICE_DISCONNECTED until backend storage exists. */
    val attachments: List<AttachmentMetadata>? = null,
)

data class ProviderEvidence(
    val state: String,
    val provider: String? = null,
    val referenceId: String? = null,
)

data class TaskReceipt(
    val taskId: String,
    val status: String,
    val verificationState: String,
    val approvalRequired: Boolean,
    val cancellable: Boolean,
    val createdAt: String,
    val providerEvidence: ProviderEvidence,
)

data class TaskPlanStep(
    val ordinal: Int,
    val agent: String,
    val action: String,
    val requiresApproval: Boolean,
    val externalAction: Boolean,
)

data class TaskPlan(val title: String, val intent: String, val steps: List<TaskPlanStep>)

data class PlanProvider(
    val name: String,
    val model: String,
    val inferenceVerified: Boolean,
    val externalActionsExecuted: Boolean,
)

data class PlanTaskData(
    val conversationId: String,
    val messageId: String,
    val reply: String,
    val plan: TaskPlan,
    val taskReceipt: TaskReceipt,
    val provider: PlanProvider,
)

data class RemoteTaskWire(
    val id: String,
    val title: String,
    val intent: String,
    val status: String,
    @param:Json(name = "error_message") val errorMessage: String? = null,
    val receipt: TaskReceipt,
)

data class RemoteTaskStep(
    val id: String,
    val ordinal: Int,
    val agent: String,
    val action: String,
    val status: String,
    val requiresApproval: Boolean,
    val externalAction: Boolean,
    val resultSummary: String? = null,
)

data class TaskDetailsData(val task: RemoteTaskWire, val steps: List<RemoteTaskStep>)

data class ApproveTaskRequest(val approved: Boolean = true)
data class CancelTaskRequest(val reason: String = "user_requested")
data class ExecutionReceipt(val state: String, val note: String)
data class ApproveTaskData(val taskReceipt: TaskReceipt, val execution: ExecutionReceipt)
data class CancelTaskData(val taskReceipt: TaskReceipt)

data class AiOutputReportRequest(
    val taskId: String,
    val provider: String,
    val modelId: String? = null,
    val output: String,
    val reportedState: String = "prepared",
    val category: String? = null,
)

data class AiOutputReportData(
    val reportId: String,
    val acceptedState: String,
    val evidence: ProviderEvidence,
    val taskTransitionApplied: Boolean,
)

data class BillingVerificationRequest(
    val packageName: String,
    val productId: String,
    val purchaseToken: String,
)

data class VerifiedEntitlement(
    val key: String,
    val state: String,
    val productId: String,
    val provider: String,
    val verifiedAt: String,
    val expiresAt: String? = null,
    val mode: String,
    val grantsAccess: Boolean,
)

data class BillingVerificationData(
    val entitlement: VerifiedEntitlement,
    val providerEvidence: ProviderEvidence,
)

data class IntegrationWireState(
    val status: String,
    val mode: String,
    val configured: Boolean,
    val verified: Boolean,
    val evidence: String,
    val model: String? = null,
    val adapterImplemented: Boolean? = null,
)

data class IntegrationListData(
    val checkedAt: String,
    val environment: String,
    val integrations: Map<String, IntegrationWireState>,
)

data class DeleteAccountRequest(val confirm: Boolean = true)

data class AccountDeletionData(
    val deletionId: String,
    val status: String,
    val identityProviderAccount: String,
    val applicationData: String,
    val completedAt: String,
)
