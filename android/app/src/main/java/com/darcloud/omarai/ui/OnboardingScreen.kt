package com.darcloud.omarai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val interests = listOf(
    "Personal Assistant", "Business", "Home Services", "Learning",
    "Selling", "Shopping", "Money", "Communication",
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.primaryContainer.copy(alpha = .22f))),
        ),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(52.dp))
            OmarMark(size = 72)
            Spacer(Modifier.height(28.dp))
            when (page) {
                0 -> WelcomePage()
                1 -> InterestPage(selected) { item ->
                    selected = if (item in selected) selected - item else selected + item
                }
                2 -> IntegrationPage()
                else -> PermissionPage()
            }
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (page > 0) {
                    OutlinedButton(onClick = { page-- }, modifier = Modifier.weight(1f)) { Text("Back") }
                }
                Button(
                    onClick = { if (page < 3) page++ else onFinish() },
                    modifier = Modifier.weight(1f),
                ) { Text(if (page < 3) "Continue" else "Open Omar AI") }
            }
            Spacer(Modifier.height(16.dp))
            Text("${page + 1} of 4", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WelcomePage() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Meet Omar AI", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Your AI for life and business.", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp))
        Text(
            "Ask by text, voice, photo, or file. Omar AI routes supported requests, shows approvals, and reports exactly what did—or did not—happen.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InterestPage(selected: Set<String>, toggle: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("What matters to you?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Optional. These choices do not enable unavailable integrations.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        interests.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { item ->
                    Surface(
                        modifier = Modifier.weight(1f).padding(vertical = 5.dp).clickable { toggle(item) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (item in selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(item, modifier = Modifier.padding(14.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun IntegrationPage() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Connect only what you need", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        OnboardingCard(Icons.Rounded.CloudOff, "Optional service", "The app works with local CRM records. AI tasks require a configured Omar AI backend.")
        OnboardingCard(Icons.Rounded.Lock, "Clear states", "Integrations always show Connected, Disconnected, Pending, Degraded, or Failed.")
        HonestInfo("Live phone calls, financial accounts, user messaging, and provider marketplace actions are not included in this v1.")
    }
}

@Composable
private fun PermissionPage() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("You stay in control", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .8f)),
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
