package com.darcloud.omarai.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String?,
    val email: String?,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "leads")
data class LeadEntity(
    @PrimaryKey val id: String,
    val customerId: String?,
    val title: String,
    val source: String?,
    val status: String,
    val estimatedValueMinor: Long?,
    val currencyCode: String,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey val id: String,
    val customerId: String?,
    val title: String,
    val status: String,
    val scheduledAtEpochMs: Long?,
    val completedAtEpochMs: Long?,
    val quotedMinor: Long?,
    val currencyCode: String,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "invoices")
data class InvoiceEntity(
    @PrimaryKey val id: String,
    val customerId: String?,
    val label: String,
    val status: String,
    val totalMinor: Long,
    val paidMinor: Long,
    val currencyCode: String,
    val createdAtEpochMs: Long,
    val paymentReceivedAtEpochMs: Long?,
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val remoteId: String?,
    val title: String,
    val agent: String,
    val status: String,
    val startedAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val actionsPerformed: String,
    val permissionsUsed: String,
    val result: String?,
    val error: String?,
    val cancellable: Boolean,
    val approvalPrompt: String?,
    val providerEvidenceId: String?,
    val approvalIdempotencyKey: String? = null,
    val cancellationIdempotencyKey: String? = null,
)

@Entity(tableName = "audit_events")
data class AuditEventEntity(
    @PrimaryKey val id: String,
    val taskId: String?,
    val eventType: String,
    val detail: String,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val role: String,
    val text: String,
    val agent: String?,
    val taskId: String?,
    val createdAtEpochMs: Long,
    val reportable: Boolean,
)

@Entity(tableName = "ai_output_reports")
data class AiOutputReportEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val category: String,
    val note: String?,
    val status: String,
    val remoteReportId: String?,
    val createdAtEpochMs: Long,
)

enum class TaskStatus {
    RECEIVED,
    PLANNED,
    AWAITING_APPROVAL,
    SCHEDULED,
    QUEUED,
    RUNNING,
    EXECUTING,
    SUBMITTED,
    VERIFYING,
    COMPLETED,
    PARTIAL,
    FAILED,
    CANCELLED;

    val isTerminal: Boolean get() = this in setOf(COMPLETED, FAILED, CANCELLED)
}

enum class TaskSection { ACTIVE, WAITING_FOR_APPROVAL, SCHEDULED, COMPLETED, FAILED }

fun TaskStatus.section(): TaskSection = when (this) {
    TaskStatus.AWAITING_APPROVAL -> TaskSection.WAITING_FOR_APPROVAL
    TaskStatus.SCHEDULED -> TaskSection.SCHEDULED
    TaskStatus.COMPLETED -> TaskSection.COMPLETED
    TaskStatus.FAILED, TaskStatus.CANCELLED -> TaskSection.FAILED
    else -> TaskSection.ACTIVE
}

enum class IntegrationState {
    DISCONNECTED,
    PENDING,
    CONNECTED_TEST,
    CONNECTED_PRODUCTION,
    DEGRADED,
    FAILED,
}

object LeadStatuses {
    const val NEW = "NEW"
    const val CONTACTED = "CONTACTED"
    const val QUALIFIED = "QUALIFIED"
    const val WON = "WON"
    const val LOST = "LOST"
}

object JobStatuses {
    const val SCHEDULED = "SCHEDULED"
    const val COMPLETED = "COMPLETED"
    const val CANCELLED = "CANCELLED"
}

object InvoiceStatuses {
    const val DRAFT = "DRAFT"
    const val SENT = "SENT"
    const val PARTIAL = "PARTIAL"
    const val PAID = "PAID"
    const val VOID = "VOID"
}
