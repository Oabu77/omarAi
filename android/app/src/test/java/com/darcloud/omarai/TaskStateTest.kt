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
    private fun receipt(
        status: String,
        verificationState: String = "not_executed",
        evidenceState: String = "NONE",
        reference: String? = null,
    ) = TaskReceipt(
        taskId = "task",
        status = status,
        verificationState = verificationState,
        approvalRequired = status == "waiting_approval",
        cancellable = true,
        createdAt = "2026-08-30T00:00:00Z",
        providerEvidence = ProviderEvidence(
            state = evidenceState,
            provider = null,
            referenceId = reference,
        ),
    )

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
        assertEquals(TaskStatus.AWAITING_APPROVAL, mapBackendTaskStatus(receipt("waiting_approval")))
        assertEquals(TaskStatus.QUEUED, mapBackendTaskStatus(receipt("queued")))
        assertEquals(TaskStatus.RUNNING, mapBackendTaskStatus(receipt("running")))
        assertEquals(
            TaskStatus.VERIFYING,
            mapBackendTaskStatus(receipt("completed", "provider_verified", "PROVIDER_VERIFIED")),
        )
        assertEquals(
            TaskStatus.COMPLETED,
            mapBackendTaskStatus(receipt("completed", "provider_verified", "PROVIDER_VERIFIED", "provider-123")),
        )
    }

    @Test fun everyDocumentedBackendStateMapsDeterministically() {
        val expected = mapOf(
            "received" to TaskStatus.RECEIVED,
            "planning" to TaskStatus.RECEIVED,
            "planned" to TaskStatus.PLANNED,
            "waiting_approval" to TaskStatus.AWAITING_APPROVAL,
            "scheduled" to TaskStatus.SCHEDULED,
            "queued" to TaskStatus.QUEUED,
            "running" to TaskStatus.RUNNING,
            "executing" to TaskStatus.EXECUTING,
            "submitted" to TaskStatus.SUBMITTED,
            "verifying" to TaskStatus.VERIFYING,
            "partial" to TaskStatus.PARTIAL,
            "failed" to TaskStatus.FAILED,
            "cancelled" to TaskStatus.CANCELLED,
        )
        expected.forEach { (wire, local) ->
            assertEquals(wire, local, mapBackendTaskStatus(receipt(wire)))
            assertEquals("status matching is case-insensitive", local, mapBackendTaskStatus(receipt(wire.uppercase())))
        }
        assertEquals(TaskStatus.FAILED, mapBackendTaskStatus(receipt("unknown_future_state")))
    }

    @Test fun completionRequiresAllThreeIndependentEvidenceSignals() {
        val complete = receipt("completed", "provider_verified", "PROVIDER_VERIFIED", "receipt-9")
        assertEquals(TaskStatus.COMPLETED, mapBackendTaskStatus(complete))
        assertEquals(
            TaskStatus.VERIFYING,
            mapBackendTaskStatus(complete.copy(verificationState = "not_executed")),
        )
        assertEquals(
            TaskStatus.VERIFYING,
            mapBackendTaskStatus(
                complete.copy(providerEvidence = complete.providerEvidence.copy(state = "NONE")),
            ),
        )
        assertEquals(
            TaskStatus.VERIFYING,
            mapBackendTaskStatus(
                complete.copy(providerEvidence = complete.providerEvidence.copy(referenceId = "   ")),
            ),
        )
    }
}
