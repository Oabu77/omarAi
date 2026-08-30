package com.darcloud.omarai.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.darcloud.omarai.data.local.IntegrationState
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class SettingsPage { ROOT, PLANS, INTEGRATIONS, PRIVACY, ABOUT }

@Composable
fun SettingsScreen(viewModel: OmarViewModel, modifier: Modifier = Modifier) {
    var page by remember { mutableStateOf(SettingsPage.ROOT) }
    Column(modifier.fillMaxSize()) {
        if (page == SettingsPage.ROOT) {
            PageTitle("Settings", "Plans, connections, privacy, and product status", Icons.Rounded.Settings)
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { SettingsEntry(Icons.Rounded.Payments, "Plans & billing", "Google Play products and verified entitlement") { page = SettingsPage.PLANS } }
                item { SettingsEntry(Icons.Rounded.Cloud, "Integration status", "Connected, disconnected, test, production, and failed") { page = SettingsPage.INTEGRATIONS } }
                item { SettingsEntry(Icons.Rounded.PrivacyTip, "Privacy & data", "Export and delete local data") { page = SettingsPage.PRIVACY } }
                item { SettingsEntry(Icons.Rounded.Info, "About & legal", "Release scope and configured legal links") { page = SettingsPage.ABOUT } }
                item { HonestInfo("This v1 does not create accounts or include live calls, linked finance, user messaging, marketplace booking, external payments, or company filings.") }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { page = SettingsPage.ROOT }) { Icon(Icons.Rounded.ArrowBack, "Back") }
                Text(when (page) { SettingsPage.PLANS -> "Plans & billing"; SettingsPage.INTEGRATIONS -> "Integrations"; SettingsPage.PRIVACY -> "Privacy & data"; else -> "About & legal" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
private fun SettingsEntry(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

@Composable
private fun BillingScreen(viewModel: OmarViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    val state by viewModel.billing.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            HonestInfo("Prices are loaded from Google Play. A purchase never unlocks a plan until the backend verifies its token and returns evidence.")
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
            item { EmptyState("Paid plans unavailable", "Google Play did not return active Pro or Business offers. No price is invented.") }
        } else {
            state.products.forEach { product ->
                item {
                    PlanCard(product.title, product.description, product.formattedPrice, product, enabled = activity != null && viewModel.apiConfigured && viewModel.authenticatedSessionAvailable) {
                        activity?.let { viewModel.launchPurchase(it, product) }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = viewModel::restorePurchases, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Refresh, null); Text(" Restore purchases") }
        }
        item {
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?package=${context.packageName}"))) }, modifier = Modifier.fillMaxWidth()) { Text("Manage subscriptions in Google Play") }
        }
    }
}

@Composable
private fun PlanCard(title: String, detail: String, price: String, product: PlayProduct?, enabled: Boolean, buy: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(price, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            if (product != null) Button(onClick = buy, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(if (enabled) "Continue with Google Play" else "Verified sign-in and backend required") }
        }
    }
}

@Composable
private fun IntegrationScreen(viewModel: OmarViewModel) {
    val billing by viewModel.billing.collectAsStateWithLifecycle()
    val remote by viewModel.remoteIntegrations.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            IntegrationCard(
                "Omar AI service",
                if (viewModel.apiConfigured) IntegrationState.PENDING else IntegrationState.DISCONNECTED,
                if (viewModel.apiConfigured && viewModel.authenticatedSessionAvailable) "Configured with an authenticated session; use Refresh for live states." else if (viewModel.apiConfigured) "Configured, but this v1 has no authenticated sign-in session." else "No OMAR_API_BASE_URL was configured at build time.",
            )
        }
        item {
            IntegrationCard(
                "Authentication",
                if (viewModel.authenticatedSessionAvailable) IntegrationState.PENDING else IntegrationState.DISCONNECTED,
                if (viewModel.authenticatedSessionAvailable) "An in-memory session exists; backend verification is still required." else "No sign-in flow is implemented in Android v1.",
            )
        }
        item {
            IntegrationCard(
                "Google Play Billing",
                when {
                    !billing.playServiceConnected -> IntegrationState.DISCONNECTED
                    billing.products.isEmpty() || !viewModel.authenticatedSessionAvailable -> IntegrationState.PENDING
                    BuildConfig.DEBUG -> IntegrationState.CONNECTED_TEST
                    else -> IntegrationState.CONNECTED_PRODUCTION
                },
                billing.message,
            )
        }
        remote.forEach { integration -> item { IntegrationCard(integration.displayName, parseIntegration(integration.state), integration.detail ?: "No detail returned") } }
        item { Text("Coming later", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        listOf("AI phone receptionist", "Financial account connections", "User messaging and calls", "Home-service provider marketplace", "External customer payments").forEach { name ->
            item { IntegrationCard(name, IntegrationState.DISCONNECTED, "Coming later — disabled in this v1.") }
        }
        item { Button(onClick = viewModel::refreshIntegrations, enabled = viewModel.apiConfigured && viewModel.authenticatedSessionAvailable && !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Refresh, null); Text(" Refresh verified states") } }
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
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
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
    var exportJson by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRemoteDelete by remember { mutableStateOf(false) }
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
            title = { Text("Delete all local Omar AI data?") },
            text = { Text("This permanently removes local CRM records, conversations, tasks, audit events, saved output reports, temporary Omar camera previews, legacy picker grants, and app preferences from this device. It does not claim to delete remote data.") },
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
        item { HonestInfo("This build has no account-creation or sign-in flow. Local records remain on this device unless you export or delete them. Android backup is disabled for app data.") }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Data used in v1", fontWeight = FontWeight.Bold)
                    Text("Prompts, temporary camera previews, selected-file metadata, voice-to-text results, CRM records, tasks, audit events, billing purchase tokens, and output reports—only when you use the matching feature. Raw selected photos/files are not uploaded in this v1.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    Text("No ads SDK is included. Raw card data is never collected by this app.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        item { Button(onClick = { scope.launch { exportJson = viewModel.exportJson(); createDocument.launch("omar-ai-export-${LocalDate.now()}.json") } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Download, null); Text(" Export local data") } }
        item { OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.DeleteForever, null); Text(" Delete local data") } }
        if (viewModel.apiConfigured) item { OutlinedButton(onClick = { confirmRemoteDelete = true }, enabled = viewModel.authenticatedSessionAvailable, modifier = Modifier.fillMaxWidth()) { Text(if (viewModel.authenticatedSessionAvailable) "Delete remote account" else "Remote deletion requires sign-in") } }
        status?.let { item { HonestInfo(it) } }
    }
}

@Composable
private fun AboutScreen() {
    val context = LocalContext.current
    val privacyConfigured = !BuildConfig.PRIVACY_POLICY_URL.contains("example.invalid")
    val deletionConfigured = !BuildConfig.ACCOUNT_DELETION_URL.contains("example.invalid")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OmarMark(size = 76); Spacer(Modifier.height(8.dp)); Text("Omar AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Version ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { HonestInfo("Release scope: AI request routing, exact task states, local CRM/dashboard, contextual camera and microphone, system photo/file pickers, output reporting, data export/deletion, integrations, and server-verified Play Billing hooks.") }
        item { OutlinedButton(enabled = privacyConfigured, onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_POLICY_URL))) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Lock, null); Text(if (privacyConfigured) " Privacy policy" else " Privacy policy URL not configured") } }
        item { OutlinedButton(enabled = deletionConfigured, onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.ACCOUNT_DELETION_URL))) }, modifier = Modifier.fillMaxWidth()) { Text(if (deletionConfigured) "External deletion page" else "Deletion URL not configured") } }
        item { Text("© DarCloud LLC. Omar AI™ — Your AI for life and business.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private fun parseIntegration(value: String): IntegrationState = IntegrationState.entries.firstOrNull { it.name == value.uppercase() } ?: IntegrationState.FAILED
