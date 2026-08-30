package com.darcloud.omarai.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DomainAdd
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darcloud.omarai.data.local.ChatMessageEntity
import com.darcloud.omarai.data.local.OmarRepository
import com.darcloud.omarai.data.local.PendingAttachment
import java.io.File
import java.io.FileOutputStream
import java.time.LocalTime
import java.util.Locale
import java.util.UUID

@Composable
fun HomeScreen(
    viewModel: OmarViewModel,
    modifier: Modifier = Modifier,
    onOpenBusiness: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var attachments by remember { mutableStateOf(listOf<PendingAttachment>()) }
    var usedPermissions by remember { mutableStateOf(setOf<String>()) }
    var showCameraPermissionDialog by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf<String?>(null) }
    var reportMessage by remember { mutableStateOf<ChatMessageEntity?>(null) }
    val latestAttachments by rememberUpdatedState(attachments)
    DisposableEffect(Unit) {
        onDispose { latestAttachments.forEach(::deleteCameraPreview) }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) input = TextFieldValue(spoken)
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            runCatching {
                val file = File(context.cacheDir, "camera-${UUID.randomUUID()}.jpg")
                FileOutputStream(file).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
                attachments = attachments + PendingAttachment(Uri.fromFile(file), file.name, "image/jpeg", "CAMERA")
                usedPermissions = usedPermissions + "CAMERA"
            }.onFailure { permissionDenied = "The camera photo could not be attached." }
        }
    }
    fun launchSpeech() {
        runCatching {
            speechLauncher.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Omar AI")
                },
            )
        }.onFailure { permissionDenied = "Voice recognition is not available on this device." }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(null) else permissionDenied = "Camera permission was not granted. No photo was taken."
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { attachments = attachments + OmarRepository.attachmentFromUri(context.contentResolver, it, "PHOTO_PICKER") }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            attachments = attachments + OmarRepository.attachmentFromUri(context.contentResolver, it, "FILE_PICKER")
        }
    }

    if (showCameraPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showCameraPermissionDialog = false },
            title = { Text("Use your camera?") },
            text = { Text("Omar AI uses the camera only to take the photo you requested. The preview is stored temporarily on this device. Raw photo upload is disabled until a real file service is connected.") },
            confirmButton = {
                Button(onClick = {
                    showCameraPermissionDialog = false
                    cameraPermission.launch(Manifest.permission.CAMERA)
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { showCameraPermissionDialog = false }) { Text("Not now") } },
        )
    }
    permissionDenied?.let { notice ->
        AlertDialog(
            onDismissRequest = { permissionDenied = null },
            title = { Text("Feature did not start") },
            text = { Text(notice) },
            confirmButton = { TextButton(onClick = { permissionDenied = null }) { Text("OK") } },
            dismissButton = {
                TextButton(onClick = {
                    permissionDenied = null
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }) { Text("App settings") }
            },
        )
    }
    reportMessage?.let { message ->
        ReportOutputDialog(message, onDismiss = { reportMessage = null }) { category, note ->
            viewModel.report(message, category, note)
            reportMessage = null
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .22f))),
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HomeHeader(onOpenTasks, onOpenSettings)
        }
        if (messages.isNotEmpty()) {
            item {
                Text("Conversation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
            }
            items(messages.takeLast(6), key = { it.id }) { message ->
                MessageCard(message, onReport = { reportMessage = message })
            }
        }
        item {
            AssistantInput(
                value = input,
                onValueChange = { input = it },
                attachments = attachments,
                onRemoveAttachment = { value -> deleteCameraPreview(value); attachments = attachments - value },
                busy = busy,
                onMic = ::launchSpeech,
                onCamera = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) cameraLauncher.launch(null)
                    else showCameraPermissionDialog = true
                },
                onPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onFile = { filePicker.launch(arrayOf("application/pdf", "text/plain", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) },
                onSubmit = {
                    viewModel.submit(input.text, attachments, usedPermissions)
                    input = TextFieldValue("")
                    attachments.forEach(::deleteCameraPreview)
                    attachments = emptyList()
                    usedPermissions = emptySet()
                },
            )
        }
        item {
            QuickActions(
                onBusiness = onOpenBusiness,
                onQuote = { input = TextFieldValue("Prepare a preliminary photo-based job estimate.") },
                onCompany = { input = TextFieldValue("Help me prepare a company plan for this idea: ") },
                onTasks = onOpenTasks,
            )
        }
    }
}

private fun deleteCameraPreview(attachment: PendingAttachment) {
    if (attachment.source == "CAMERA" && attachment.uri.scheme == "file") {
        attachment.uri.path?.let { path -> runCatching { File(path).delete() } }
    }
}

@Composable
private fun HomeHeader(onTasks: () -> Unit, onSettings: () -> Unit) {
    val greeting = when (LocalTime.now().hour) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OmarMark()
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(greeting, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            Text("Omar AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onTasks) { Icon(Icons.Rounded.NotificationsNone, "Open tasks") }
        IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Open settings") }
    }
}

@Composable
private fun MessageCard(message: ChatMessageEntity, onReport: () -> Unit) {
    val assistant = message.role == "ASSISTANT"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = if (assistant) Arrangement.Start else Arrangement.End,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(if (assistant) .94f else .82f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (assistant) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(Modifier.padding(15.dp)) {
                Text(if (assistant) (message.agent ?: "Omar AI") else "You", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(5.dp))
                Text(message.text)
                if (message.reportable) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onReport) { Icon(Icons.Rounded.Flag, null, Modifier.size(16.dp)); Text(" Report output") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    attachments: List<PendingAttachment>,
    onRemoveAttachment: (PendingAttachment) -> Unit,
    busy: Boolean,
    onMic: () -> Unit,
    onCamera: () -> Unit,
    onPhoto: () -> Unit,
    onFile: () -> Unit,
    onSubmit: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(8.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("What do you want Omar AI to do?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth().height(126.dp),
                placeholder = { Text("Ask, describe, or add a photo or file…") },
                shape = RoundedCornerShape(18.dp),
            )
            attachments.forEach { attachment ->
                Surface(Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (attachment.mimeType.startsWith("image/")) Icons.Rounded.Image else Icons.Rounded.AttachFile, null)
                        Text(attachment.displayName, Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 1)
                        IconButton(onClick = { onRemoveAttachment(attachment) }) { Icon(Icons.Rounded.Close, "Remove attachment") }
                    }
                }
            }
            if (attachments.any { it.mimeType.startsWith("image/") }) {
                Text(
                    "Photo estimates are preliminary ranges—not guaranteed professional assessments.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OmarAmber,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedIconButton(onClick = onMic) { Icon(Icons.Rounded.Mic, "Voice input") }
                OutlinedIconButton(onClick = onCamera) { Icon(Icons.Rounded.CameraAlt, "Take photo") }
                OutlinedIconButton(onClick = onPhoto) { Icon(Icons.Rounded.Image, "Choose photo") }
                OutlinedIconButton(onClick = onFile) { Icon(Icons.Rounded.AttachFile, "Choose file") }
                Spacer(Modifier.weight(1f))
                FilledIconButton(
                    onClick = onSubmit,
                    enabled = !busy && (value.text.isNotBlank() || attachments.isNotEmpty()),
                ) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Send, "Route request") }
            }
            Text("ASK → PLAN → APPROVE → ACT → VERIFY → REPORT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun QuickActions(onBusiness: () -> Unit, onQuote: () -> Unit, onCompany: () -> Unit, onTasks: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text("Quick actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        listOf(
            Triple(Icons.Rounded.BusinessCenter, "Run my business", onBusiness),
            Triple(Icons.Rounded.CameraAlt, "Quote a job", onQuote),
            Triple(Icons.Rounded.DomainAdd, "Build a company", onCompany),
            Triple(Icons.Rounded.TaskAlt, "Command Center", onTasks),
        ).chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (icon, label, action) ->
                    OutlinedButton(onClick = action, modifier = Modifier.weight(1f).padding(vertical = 4.dp), shape = RoundedCornerShape(14.dp)) {
                        Icon(icon, null, Modifier.size(18.dp)); Text(" $label")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportOutputDialog(
    message: ChatMessageEntity,
    onDismiss: () -> Unit,
    onSubmit: (String, String?) -> Unit,
) {
    var category by remember { mutableStateOf("INACCURATE") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report AI output") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose the closest reason. The report includes this output for review.")
                listOf("INACCURATE", "OFFENSIVE", "UNSAFE", "PRIVACY", "OTHER").forEach { value ->
                    OutlinedButton(onClick = { category = value }, modifier = Modifier.fillMaxWidth()) {
                        val label = value.lowercase().replaceFirstChar { it.uppercase() }
                        Text(if (category == value) "✓ $label" else label)
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Optional note") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onSubmit(category, note) }) { Text("Save report") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
