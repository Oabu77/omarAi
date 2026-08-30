package com.darcloud.omarai

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayPolicyStaticTest {
    private fun source(relative: String): String {
        val candidates = listOf(File(relative), File("app/$relative"), File("../app/$relative"))
        return candidates.firstOrNull(File::exists)?.readText()
            ?: error("Could not find policy source: $relative")
    }

    @Test fun manifestUsesOnlyContextualV1Permissions() {
        val manifest = source("src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("android.permission.CAMERA"))
        assertFalse(manifest.contains("android.permission.RECORD_AUDIO"))
        listOf(
            "READ_CONTACTS", "WRITE_CONTACTS", "READ_SMS", "READ_CALL_LOG", "MANAGE_EXTERNAL_STORAGE",
            "ACCESS_BACKGROUND_LOCATION", "POST_NOTIFICATIONS", "BIND_ACCESSIBILITY_SERVICE",
        ).forEach { forbidden -> assertFalse("Manifest must not request $forbidden", manifest.contains(forbidden)) }
    }

    @Test fun billingNeverAcknowledgesOrGrantsLocally() {
        val billing = source("src/main/java/com/darcloud/omarai/data/billing/BillingManager.kt")
        assertFalse(billing.contains("acknowledgePurchase"))
        assertTrue(billing.contains("value.entitlement.state"))
        assertTrue(billing.contains("value.entitlement.grantsAccess"))
        assertTrue(billing.contains("value.providerEvidence.state == \"PROVIDER_VERIFIED\""))
        assertTrue(billing.contains("value.providerEvidence.referenceId"))
        assertTrue(billing.contains("No entitlement was granted"))
    }

    @Test fun releaseUxContainsRequiredTruthfulnessControls() {
        val home = source("src/main/java/com/darcloud/omarai/ui/HomeScreen.kt")
        val tasks = source("src/main/java/com/darcloud/omarai/ui/TaskCenterScreen.kt")
        val privacy = source("src/main/java/com/darcloud/omarai/ui/SettingsScreen.kt")
        assertTrue(home.contains("not guaranteed professional assessments"))
        assertTrue(home.contains("Report AI output"))
        assertTrue(tasks.contains("Submitted ≠ completed"))
        assertTrue(privacy.contains("Export local data"))
        assertTrue(privacy.contains("Delete local data"))
    }

    @Test fun authenticatedMutationsUseBackendContractGuards() {
        val api = source("src/main/java/com/darcloud/omarai/data/api/OmarApi.kt")
        val client = source("src/main/java/com/darcloud/omarai/data/api/OmarApiClient.kt")
        assertTrue(api.contains("Idempotency-Key"))
        assertTrue(api.contains("X-Deletion-Confirmation"))
        assertTrue(api.contains("hasBody = true"))
        assertTrue(client.contains("hasAuthenticatedSession"))
        assertTrue(client.contains("ApiEnvelope"))
        val repository = source("src/main/java/com/darcloud/omarai/data/local/OmarRepository.kt")
        assertTrue(repository.contains("approvalIdempotencyKey"))
        assertTrue(repository.contains("cancellationIdempotencyKey"))
    }

    @Test fun localDeletionClearsTemporaryMediaAndUriGrants() {
        val repository = source("src/main/java/com/darcloud/omarai/data/local/OmarRepository.kt")
        val home = source("src/main/java/com/darcloud/omarai/ui/HomeScreen.kt")
        assertTrue(repository.contains("startsWith(\"camera-\")"))
        assertTrue(repository.contains("releasePersistableUriPermission"))
        assertFalse(home.contains("takePersistableUriPermission"))
    }

    @Test fun remoteDeletionDisplaysBackendDeletionIdentifier() {
        val viewModel = source("src/main/java/com/darcloud/omarai/ui/OmarViewModel.kt")
        assertTrue(viewModel.contains("value.deletionId"))
        assertFalse(viewModel.contains("value.requestId"))
    }

    @Test fun localDeletionAlsoClearsPreferencesAndExportChecksForNullStreams() {
        val viewModel = source("src/main/java/com/darcloud/omarai/ui/OmarViewModel.kt")
        val settings = source("src/main/java/com/darcloud/omarai/ui/SettingsScreen.kt")
        assertTrue(viewModel.contains("container.preferences.clear()"))
        assertTrue(settings.contains("?: error(\"The selected destination could not be opened.\")"))
    }
}
