package com.darcloud.omarai.data.local

import com.darcloud.omarai.data.api.TaskReceipt

fun mapBackendTaskStatus(receipt: TaskReceipt): TaskStatus {
    val parsed = when (receipt.status.lowercase()) {
        "received" -> TaskStatus.RECEIVED
        "planning" -> TaskStatus.RECEIVED
        "planned" -> TaskStatus.PLANNED
        "waiting_approval" -> TaskStatus.AWAITING_APPROVAL
        "scheduled" -> TaskStatus.SCHEDULED
        "queued" -> TaskStatus.QUEUED
        "running" -> TaskStatus.RUNNING
        "executing" -> TaskStatus.EXECUTING
        "submitted" -> TaskStatus.SUBMITTED
        "verifying" -> TaskStatus.VERIFYING
        "partial" -> TaskStatus.PARTIAL
        "completed" -> TaskStatus.COMPLETED
        "failed" -> TaskStatus.FAILED
        "cancelled" -> TaskStatus.CANCELLED
        else -> TaskStatus.FAILED
    }
    return if (parsed == TaskStatus.COMPLETED && (
            receipt.verificationState != "provider_verified" ||
                receipt.providerEvidence.state != "PROVIDER_VERIFIED" ||
                receipt.providerEvidence.referenceId.isNullOrBlank()
        )) {
        TaskStatus.VERIFYING
    } else parsed
}
