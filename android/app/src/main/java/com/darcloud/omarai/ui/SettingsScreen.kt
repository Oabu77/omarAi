package com.darcloud.omarai.ui

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darcloud.omarai.BuildConfig
import com.darcloud.omarai.data.billing.PlayProduct
import com.darcloud.omarai.data.auth.SessionState
import com.darcloud.omarai.data.local.IntegrationState
import kotlinx.coroutines.launch
import java.net.URI
import java.time.LocalDate
import java.util.Locale

private enum class SettingsPage { ROOT, PLANS, INTEGRATIONS, PRIVACY, ABOUT }

@Composable
fun SettingsScreen(viewModel: OmarViewModel, modifier: Modifier = Modifier) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.ROOT) }
    Column(modifier.fillMaxSize()) {
        if (page == SettingsPage.ROOT) {
            PageTitle("Settings", "Plans, connections, privacy, and product status", Icons.Rounded.Settings)
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { SettingsModeCard() }
                item { SettingsSectionLabel("Product") }
                item { SettingsEntry(Icons.Rounded.Payments, "Plans", "Google Play Pro and Business subscriptions") { page = SettingsPage.PLANS } }
                item { SettingsEntry(Icons.Rounded.Cloud, "Integration status", "Connected, disconnected, test, production, and failed") { page = SettingsPage.INTEGRATIONS } }
                item { SettingsSectionLabel("Privacy & support") }
                item { SettingsEntry(Icons.Rounded.PrivacyTip, "Privacy & data", "Export and delete local data") { page = SettingsPage.PRIVACY } }
                item { SettingsEntry(Icons.Rounded.Info, "About & legal", "Release scope and configured legal links") { page = SettingsPage.ABOUT } }
                item { HonestInfo("Omar AI creates a private Firebase guest session only when you use an online feature. Provider actions remain disabled until their verified connections are available.") }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { page = SettingsPage.ROOT }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Text(when (page) { SettingsPage.PLANS -> "Plans"; SettingsPage.INTEGRATIONS -> "Integrations"; SettingsPage.PRIVACY -> "Privacy & data"; else -> "About & legal" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            when (page) {
                SettingsPage.PLANS -> BillingScreen(viewModel)
                SettingsPage.INTEGRATIONS -> IntegrationScreen(viewModel)
                SettingsPage.PRIVACY -> PrivacyScreen(viewModel)
                SettingsPage.ABOUT -> AboutScreen()
                else -> Unit
            }
        }
    }
}

@Composable
private fun SettingsModeCard() {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .18f)),
    ) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Rounded.Lock, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text("Local-first, connected when needed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Local records remain on this device. Online requests use a verified guest session.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill("LOCAL", MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SettingsSectionLabel(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

@Composable
private fun SettingsEntry(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .38f)),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, Modifier.padding(9.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

@Composable
private fun BillingScreen(viewModel: OmarViewModel) {
    val state by viewModel.billing.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    LaunchedEffect(Unit) { viewModel.startBilling() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            HonestInfo("Prices and checkout come directly from Google Play. Paid access is granted only after the Omar AI server verifies the purchase and subscription lifecycle.")
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f))) {
                Column(Modifier.padding(16.dp)) {
                    Text("Current entitlement", style = MaterialTheme.typography.labelLarge)
                    Text(state.verifiedEntitlement ?: "No server-verified paid plan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.entitlementEvidenceId?.let { Text("Evidence: $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item { PlanCard("Free", "Local CRM records, task history, privacy controls", "Included", null, enabled = false) {} }
        if (state.products.isEmpty()) {
            item { EmptyState("Plans are not available yet", state.message) }
        } else {
            state.products.forEach { product ->
                item {
                    PlanCard(product.title, product.description, product.formattedPrice, product, enabled = state.playServiceConnected && activity != null) {
                        activity?.let { viewModel.launchPurchase(it, product) }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = viewModel::restorePurchases, enabled = state.playServiceConnected, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Refresh, null); Text(" Restore Google Play purchases") }
        }
    }
}

@Composable
private fun PlanCard(title: String, detail: String, price: String, product: PlayProduct?, enabled: Boolean, buy: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .38f))) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(price, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            if (product != null) Button(onClick = buy, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(if (enabled) "Continue in Google Play" else "Unavailable") }
        }
    }
}

@Composable
private fun IntegrationScreen(viewModel: OmarViewModel) {
    val billing by viewModel.billing.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val serviceState by viewModel.serviceState.collectAsStateWithLifecycle()
    val remote by viewModel.remoteIntegrations.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            IntegrationCard(
                "Omar AI service",
                when (serviceState) { ServiceConnectionState.CONNECTED -> IntegrationState.CONNECTED_PRODUCTION; ServiceConnectionState.PENDING -> IntegrationState.PENDING; ServiceConnectionState.FAILED -> IntegrationState.FAILED; ServiceConnectionState.DISCONNECTED -> IntegrationState.DISCONNECTED },
                when (serviceState) { ServiceConnectionState.CONNECTED -> "The authenticated integrations request succeeded; provider details are shown below."; ServiceConnectionState.PENDING -> "API configured, but no live authenticated response has been verified on this screen yet."; ServiceConnectionState.FAILED -> "The last live service check failed. Retry to verify recovery."; ServiceConnectionState.DISCONNECTED -> "No OMAR_API_BASE_URL was configured at build time." },
            )
        }
        item {
            IntegrationCard(
                "Authentication",
                when (authState) { SessionState.AUTHENTICATED -> IntegrationState.CONNECTED_PRODUCTION; SessionState.CONNECTING -> IntegrationState.PENDING; SessionState.FAILED -> IntegrationState.FAILED; SessionState.DISCONNECTED -> IntegrationState.PENDING },
                when (authState) { SessionState.AUTHENTICATED -> "Firebase returned a verified, refreshable guest session."; SessionState.CONNECTING -> "Creating or refreshing the private guest session…"; SessionState.FAILED -> "Firebase authentication failed. Retry an online feature."; SessionState.DISCONNECTED -> "No account has been created yet; online use starts a guest session." },
            )
        }
        item {
            IntegrationCard(
                "Paid plans",
                if (billing.playServiceConnected) IntegrationState.CONNECTED_PRODUCTION else if (billing.connecting) IntegrationState.PENDING else IntegrationState.DISCONNECTED,
                billing.message,
            )
        }
        remote.forEach { integration -> item { IntegrationCard(integration.displayName, parseIntegration(integration.state), integration.detail ?: "No detail returned") } }
        item { Text("Coming later", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        listOf("AI phone receptionist", "Financial account connections", "User messaging and calls", "Home-service provider marketplace", "External customer payments").forEach { name ->
            item { IntegrationCard(name, IntegrationState.DISCONNECTED, "Coming later — disabled in this v1.") }
        }
        item { Button(onClick = viewModel::refreshIntegrations, enabled = viewModel.apiConfigured && !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Refresh, null); Text(" Refresh verified states") } }
    }
}

@Composable
private fun IntegrationCard(name: String, state: IntegrationState, detail: String) {
    val color = when (state) {
        IntegrationState.CONNECTED_PRODUCTION, IntegrationState.CONNECTED_TEST -> OmarTeal
        IntegrationState.PENDING -> OmarAmber
        IntegrationState.DEGRADED -> OmarAmber
        IntegrationState.FAILED -> OmarRed
        IntegrationState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .36f))) {
        Column(Modifier.padding(15.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(name, fontWeight = FontWeight.SemiBold); StatusPill(state.name, color) }
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun PrivacyScreen(viewModel: OmarViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    var exportJson by remember { mutableStateOf<String?>(null) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmRemoteDelete by rememberSaveable { mutableStateOf(false) }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            status = runCatching {
                val stream = context.contentResolver.openOutputStream(uri)
                    ?: error("The selected destination could not be opened.")
                stream.bufferedWriter().use { it.write(exportJson.orEmpty()) }
                "Export written to the selected file."
            }.getOrElse { "Export could not be written." }
        }
        exportJson = null
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete local Omar AI records?") },
            text = { Text("This permanently removes local CRM records, conversations, tasks, audit events, saved output reports, and app preferences from this device. It also attempts to remove temporary Omar camera previews and legacy picker grants. Use Android Clear storage or uninstall to remove all app-private data. It does not claim to delete remote data.") },
            confirmButton = { Button(onClick = { viewModel.deleteLocalData(); confirmDelete = false }) { Text("Delete local data") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    if (confirmRemoteDelete) {
        AlertDialog(
            onDismissRequest = { confirmRemoteDelete = false },
            title = { Text("Delete remote Omar AI account?") },
            text = { Text("This explicitly requests permanent identity-provider and application-data deletion. It requires a verified sign-in session and backend confirmation; failure will not be reported as deletion.") },
            confirmButton = { Button(onClick = { viewModel.requestRemoteDeletion(); confirmRemoteDelete = false }) { Text("Delete remote account") } },
            dismissButton = { TextButton(onClick = { confirmRemoteDelete = false }) { Text("Cancel") } },
        )
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { HonestInfo("Local records remain on this device until you delete them; export creates a separate copy. An online feature creates a private Firebase guest account so the server can isolate and delete your remote data. Android backup is disabled for app data.") }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Data used in v2", fontWeight = FontWeight.Bold)
                    Text("Online prompts, guest account identifiers, task state, service audit evidence, and moderation reports are sent to the Omar AI service when you use those features. CRM records remain local in this release. Raw selected photos/files are not uploaded.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    Text("Google Play supplies localized plan details and purchase tokens. Tokens are sent over HTTPS for server verification, are not shown or exported, and the server persists only a one-way token hash.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    Text("No ads SDK is included. Raw card data is never collected by this app.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        item { Button(onClick = { scope.launch { exportJson = viewModel.exportJson(); createDocument.launch("omar-ai-export-${LocalDate.now()}.json") } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Download, null); Text(" Export local data") } }
        item {
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = .55f)),
            ) { Icon(Icons.Rounded.DeleteForever, null); Text(" Delete local data") }
        }
        if (viewModel.apiConfigured) item { OutlinedButton(onClick = { confirmRemoteDelete = true }, enabled = authState == SessionState.AUTHENTICATED, modifier = Modifier.fillMaxWidth()) { Text(if (authState == SessionState.AUTHENTICATED) "Delete remote account" else "No remote guest account on this device") } }
        status?.let { item { HonestInfo(it) } }
    }
}

@Composable
private fun AboutScreen() {
    val context = LocalContext.current
    val privacyConfigured = isConfiguredPublicHttpsUrl(BuildConfig.PRIVACY_POLICY_URL)
    val deletionConfigured = isConfiguredPublicHttpsUrl(BuildConfig.ACCOUNT_DELETION_URL)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OmarMark(size = 76); Spacer(Modifier.height(8.dp)); Text("Omar AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Version ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { HonestInfo("Release scope: authenticated Omar AI planning chat, exact task states, local CRM summaries, contextual camera and device speech recognition, system photo/file pickers without raw upload, local/remote data controls, verified integration status, and Google Play subscription flows with server-authoritative entitlements.") }
        item { OutlinedButton(enabled = privacyConfigured, onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, BuildConfig.PRIVACY_POLICY_URL.toUri())) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Lock, null); Text(if (privacyConfigured) " Privacy policy" else " Privacy policy URL not configured") } }
        item { OutlinedButton(enabled = deletionConfigured, onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, BuildConfig.ACCOUNT_DELETION_URL.toUri())) }, modifier = Modifier.fillMaxWidth()) { Text(if (deletionConfigured) "External deletion page" else "Deletion URL not configured") } }
        item { Text("Omar AI ${BuildConfig.VERSION_NAME} — local-first with authenticated online services.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private fun isConfiguredPublicHttpsUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    val host = uri.host?.trimEnd('.')?.lowercase(Locale.ROOT) ?: return@runCatching false
    val literal = host.removePrefix("[").removeSuffix("]")
    val documentationExamples = listOf("example.com", "example.net", "example.org")
    uri.scheme.equals("https", ignoreCase = true) &&
        uri.userInfo == null &&
        uri.rawFragment == null &&
        host.contains('.') &&
        host != "localhost" &&
        !host.endsWith(".localhost") &&
        !host.endsWith(".local") &&
        !host.endsWith(".example") &&
        !host.endsWith(".test") &&
        documentationExamples.none { reserved -> host == reserved || host.endsWith(".$reserved") } &&
        host != "invalid" &&
        !host.endsWith(".invalid") &&
        !Regex("""^\d{1,3}(?:\.\d{1,3}){3}$""").matches(literal) &&
        !literal.contains(':')
}.getOrDefault(false)

private fun parseIntegration(value: String): IntegrationState = IntegrationState.entries.firstOrNull { it.name == value.uppercase() } ?: IntegrationState.FAILED
