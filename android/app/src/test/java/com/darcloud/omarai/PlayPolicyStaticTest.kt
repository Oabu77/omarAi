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

    @Test fun connectedBuildUsesPlayBillingWithServerAuthoritativeEntitlements() {
        val billing = source("src/main/java/com/darcloud/omarai/data/billing/BillingManager.kt")
        val build = source("build.gradle.kts")
        assertTrue(build.contains("com.android.billingclient:billing-ktx:9.1.0"))
        assertTrue(billing.contains("enablePendingPurchases"))
        assertTrue(billing.contains("verifyGooglePlayPurchase"))
        assertTrue(billing.contains("entitlement.grantsAccess"))
        assertTrue(billing.contains("No paid access was granted"))
        assertFalse(billing.contains("acknowledgePurchase("))
    }

    @Test fun reflectiveMoshiContractsRemainIntactAfterReleaseShrinking() {
        val rules = source("proguard-rules.pro")
        assertTrue(rules.contains("-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations"))
        assertTrue(rules.contains("-keep class com.darcloud.omarai.data.api.** { *; }"))
    }

    @Test fun configuredReleaseApiRequiresPublicHttps() {
        val build = source("build.gradle.kts")
        assertTrue(build.contains("val apiConfigured = !hasReservedInvalidHost(omarApiBaseUrl)"))
        assertFalse(build.contains("omarApiBaseUrl.contains(\"example.invalid\")"))
        assertTrue(build.contains("check(!apiConfigured || isPublicHttpsUrl(omarApiBaseUrl))"))
        assertTrue(build.contains("OMAR_API_BASE_URL must be a public HTTPS URL when configured for release builds."))
        assertTrue(build.contains("verifyUrlValidationPolicy"))
        assertTrue(build.contains("uri.userInfo == null"))
        assertTrue(build.contains("!ipv4Literal.matches(literal)"))
        assertTrue(build.contains("!host.endsWith(\".example\")"))
        assertTrue(build.contains("!host.endsWith(\".test\")"))
        assertTrue(build.contains("https://policy.omarai.test.example/privacy"))
        assertTrue(build.contains("https://example.com/privacy"))
        assertTrue(build.contains("https://policy.example.net/privacy"))
        assertTrue(build.contains("https://www.example.org/privacy"))
    }

    @Test fun releaseSigningIsExternalAndCanBeRequiredFailClosed() {
        val build = source("build.gradle.kts")
        assertTrue(build.contains("OMAR_UPLOAD_KEYSTORE_PATH"))
        assertTrue(build.contains("OMAR_UPLOAD_KEYSTORE_PASSWORD"))
        assertTrue(build.contains("OMAR_UPLOAD_KEY_ALIAS"))
        assertTrue(build.contains("OMAR_UPLOAD_KEY_PASSWORD"))
        assertTrue(build.contains("OMAR_REQUIRE_RELEASE_SIGNING"))
        assertTrue(build.contains("!requireReleaseSigning || releaseSigningConfigured"))
        assertFalse(build.contains("storePassword = \""))
        assertFalse(build.contains("keyPassword = \""))
    }

    @Test fun releaseUxContainsRequiredTruthfulnessControls() {
        val home = source("src/main/java/com/darcloud/omarai/ui/HomeScreen.kt")
        val onboarding = source("src/main/java/com/darcloud/omarai/ui/OnboardingScreen.kt")
        val business = source("src/main/java/com/darcloud/omarai/ui/BusinessScreen.kt")
        val tasks = source("src/main/java/com/darcloud/omarai/ui/TaskCenterScreen.kt")
        val privacy = source("src/main/java/com/darcloud/omarai/ui/SettingsScreen.kt")
        assertTrue(home.contains("not guaranteed professional assessments"))
        assertTrue(home.contains("Plans are not proof that external actions occurred"))
        assertTrue(home.contains("submitted for review only if a connected service confirms receipt"))
        assertFalse(home.contains("\"Quote a job\""))
        assertFalse(home.contains("\"Build a company\""))
        assertTrue(onboarding.contains("AI-assisted plans and local business records"))
        assertTrue(onboarding.contains("private Firebase guest session"))
        assertTrue(business.contains("user-entered records, not verified bank or payment data"))
        assertTrue(home.contains("Report output"))
        assertFalse(home.contains("Report AI output"))
        assertTrue(tasks.contains("Submitted ≠ completed"))
        assertTrue(privacy.contains("Export local data"))
        assertTrue(privacy.contains("Delete local data"))
        assertTrue(privacy.contains("Google Play supplies localized plan details and purchase tokens"))
        assertTrue(privacy.contains("one-way token hash"))
        assertTrue(privacy.contains("server-authoritative entitlements"))
        assertFalse(privacy.contains("BuildConfig.PRIVACY_POLICY_URL.contains"))
        assertFalse(privacy.contains("BuildConfig.ACCOUNT_DELETION_URL.contains"))
        assertTrue(privacy.contains("isConfiguredPublicHttpsUrl"))
        assertTrue(privacy.contains("!host.endsWith(\".example\")"))
        assertTrue(privacy.contains("!host.endsWith(\".test\")"))
        assertTrue(privacy.contains("uri.userInfo == null"))
        assertTrue(privacy.contains("documentationExamples.none"))
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
        assertTrue(viewModel.contains("value.identityProviderAccount == \"DELETED\""))
        assertTrue(viewModel.contains("value.applicationData == \"DELETED\""))
        assertTrue(viewModel.contains("completion is not yet confirmed"))
        assertFalse(viewModel.contains("\"Deletion confirmed. Reference:"))
    }

    @Test fun localDeletionAlsoClearsPreferencesAndExportChecksForNullStreams() {
        val viewModel = source("src/main/java/com/darcloud/omarai/ui/OmarViewModel.kt")
        val settings = source("src/main/java/com/darcloud/omarai/ui/SettingsScreen.kt")
        assertTrue(viewModel.contains("container.preferences.clear()"))
        assertTrue(settings.contains("?: error(\"The selected destination could not be opened.\")"))
    }
}
