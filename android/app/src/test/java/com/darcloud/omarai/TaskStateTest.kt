package com.darcloud.omarai

import com.darcloud.omarai.data.local.TaskSection
import com.darcloud.omarai.data.local.TaskStatus
import com.darcloud.omarai.data.local.section
import com.darcloud.omarai.data.local.mapBackendTaskStatus
import com.darcloud.omarai.data.api.ProviderEvidence
import com.darcloud.omarai.data.api.TaskReceipt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskStateTest {
    @Test fun everyStateMapsToExactlyOneCommandCenterSection() {
        val groups = TaskStatus.entries.groupBy { it.section() }
        assertEquals(TaskStatus.entries.size, groups.values.sumOf { it.size })
        assertEquals(TaskSection.WAITING_FOR_APPROVAL, TaskStatus.AWAITING_APPROVAL.section())
        assertEquals(TaskSection.SCHEDULED, TaskStatus.SCHEDULED.section())
        assertEquals(TaskSection.COMPLETED, TaskStatus.COMPLETED.section())
        assertEquals(TaskSection.FAILED, TaskStatus.FAILED.section())
        assertEquals(TaskSection.FAILED, TaskStatus.CANCELLED.section())
    }

    @Test fun onlyConfirmedTerminalStatesAreTerminal() {
        assertTrue(TaskStatus.COMPLETED.isTerminal)
        assertTrue(TaskStatus.FAILED.isTerminal)
        assertTrue(TaskStatus.CANCELLED.isTerminal)
        assertFalse(TaskStatus.SUBMITTED.isTerminal)
        assertFalse(TaskStatus.VERIFYING.isTerminal)
        assertFalse(TaskStatus.PARTIAL.isTerminal)
    }

    @Test fun backendStatusesMapWithoutInflatingCompletion() {
        fun receipt(status: String, verified: Boolean = false, reference: String? = null) = TaskReceipt(
            taskId = "task", status = status,
            verificationState = if (verified) "provider_verified" else "not_executed",
            approvalRequired = status == "waiting_approval", cancellable = true,
            createdAt = "2026-08-30T00:00:00Z",
            providerEvidence = ProviderEvidence(
                state = if (verified) "PROVIDER_VERIFIED" else "NONE",
                provider = null,
                referenceId = reference,
            ),
        )
        assertEquals(TaskStatus.AWAITING_APPROVAL, mapBackendTaskStatus(receipt("waiting_approval")))
        assertEquals(TaskStatus.QUEUED, mapBackendTaskStatus(receipt("queued")))
        assertEquals(TaskStatus.RUNNING, mapBackendTaskStatus(receipt("running")))
        assertEquals(TaskStatus.VERIFYING, mapBackendTaskStatus(receipt("completed", verified = true, reference = null)))
        assertEquals(TaskStatus.COMPLETED, mapBackendTaskStatus(receipt("completed", verified = true, reference = "provider-123")))
    }
}
