package com.darcloud.omarai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val interests = listOf(
    "Personal Assistant", "Business", "Home Services", "Learning",
    "Selling", "Shopping", "Money", "Communication",
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.primaryContainer.copy(alpha = .22f))),
        ),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().widthIn(max = 720.dp).align(Alignment.CenterHorizontally).padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OmarMark(size = 42)
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text("Omar AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Quick setup", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = .78f)) {
                    Text("${page + 1} of 4", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
            LinearProgressIndicator(
                progress = { (page + 1) / 4f },
                modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp).align(Alignment.CenterHorizontally).padding(horizontal = 20.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Column(
                Modifier.weight(1f).fillMaxWidth().widthIn(max = 720.dp).align(Alignment.CenterHorizontally).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (page == 0) {
                    Surface(
                        shape = RoundedCornerShape(36.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .28f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .18f)),
                        shadowElevation = 10.dp,
                    ) {
                        Box(contentAlignment = Alignment.BottomCenter) {
                            OmarCompanion(
                                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 34.dp),
                                size = if (windowWidth >= 600.dp) 184 else 144,
                            )
                            Surface(
                                modifier = Modifier.padding(bottom = 12.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)),
                            ) {
                                Text(
                                    "Your AI companion",
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(if (page == 0) 26.dp else 4.dp))
                when (page) {
                    0 -> WelcomePage()
                    1 -> InterestPage(selected.toSet()) { item ->
                        selected = if (item in selected) selected - item else selected + item
                    }
                    2 -> IntegrationPage()
                    else -> PermissionPage()
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
                shadowElevation = 10.dp,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (page > 0) {
                        OutlinedButton(onClick = { page-- }, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(15.dp)) { Text("Back") }
                    }
                    Button(
                        onClick = { if (page < 3) page++ else onFinish() },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        shape = RoundedCornerShape(15.dp),
                    ) { Text(if (page < 3) "Continue" else "Open Omar AI") }
                }
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Meet Omar AI", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("AI-assisted plans and local business records.", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(
            "Enter text or use Android speech recognition. When you ask an online question, Omar AI creates a private Firebase guest session and sends your prompt to the configured service. Raw selected photos and files are not uploaded.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(22.dp))
        WelcomeFeature(Icons.Rounded.AutoAwesome, "Simple command entry", "Start with what you want to accomplish.")
        WelcomeFeature(Icons.Rounded.Route, "Visible task states", "See what was planned, approved, or verified.")
        WelcomeFeature(Icons.Rounded.Description, "Local business records", "Keep customer, lead, job, and invoice records on-device.")
    }
}

@Composable
private fun WelcomeFeature(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(icon, null, Modifier.padding(9.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InterestPage(selected: Set<String>, toggle: (String) -> Unit) {
    val columns = if (LocalConfiguration.current.fontScale >= 1.3f) 1 else 2
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("What matters to you?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Optional preview only. These choices are not saved and do not enable unavailable integrations.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        interests.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { item ->
                    val isSelected = item in selected
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 5.dp)
                            .heightIn(min = 58.dp)
                            .semantics { this.selected = isSelected }
                            .toggleable(value = isSelected, role = Role.Checkbox) { toggle(item) },
                        shape = RoundedCornerShape(17.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = .8f),
                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = .42f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            if (isSelected) Icon(Icons.Rounded.Check, null, Modifier.padding(end = 5.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(item, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            }
        }
        if (selected.isNotEmpty()) {
            Text("${selected.size} selected for this preview", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun IntegrationPage() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Connect only what you need", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("Nothing is connected during onboarding.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(16.dp))
        OnboardingCard(Icons.Rounded.CloudOff, "Optional service", "The app works with local CRM records. AI tasks require a configured Omar AI backend.")
        OnboardingCard(Icons.Rounded.Lock, "Clear states", "Integrations always show Connected, Disconnected, Pending, Degraded, or Failed.")
        HonestInfo("Live phone calls, financial accounts, user messaging, and provider marketplace actions are not included in this v1.")
    }
}

@Composable
private fun PermissionPage() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("You stay in control", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("Permissions appear only when you choose the related action.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(16.dp))
        OnboardingCard(Icons.Rounded.Mic, "Voice input", "Opens Android's speech-recognition service when you tap the microphone. Omar AI does not request microphone permission or retain audio.")
        OnboardingCard(Icons.Rounded.CameraAlt, "Camera", "Requested only when you choose to take a photo.")
        OnboardingCard(Icons.Rounded.Lock, "Photos and files", "Selected with Android system pickers; no broad storage permission is requested.")
        Text("You can export or delete local app data from Settings.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OnboardingCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) {
    Card(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .8f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f)),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) { Icon(icon, null, Modifier.padding(10.dp)) }
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
