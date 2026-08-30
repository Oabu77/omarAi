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
import androidx.core.net.toUri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DomainAdd
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darcloud.omarai.data.local.ChatMessageEntity
import com.darcloud.omarai.data.local.OmarRepository
import com.darcloud.omarai.data.local.PendingAttachment
import com.darcloud.omarai.data.local.TaskEntity
import com.darcloud.omarai.data.local.TaskSection
import com.darcloud.omarai.data.local.TaskStatus
import com.darcloud.omarai.data.local.section
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
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val business by viewModel.business.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val serviceState by viewModel.serviceState.collectAsStateWithLifecycle()
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var attachments by remember { mutableStateOf(listOf<PendingAttachment>()) }
    var usedPermissions by remember { mutableStateOf(setOf<String>()) }
    var showCameraPermissionDialog by rememberSaveable { mutableStateOf(false) }
    var permissionDenied by rememberSaveable { mutableStateOf<String?>(null) }
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
        uri?.let {
            runCatching { OmarRepository.attachmentFromUri(context.contentResolver, it, "PHOTO_PICKER") }
                .onSuccess { attachment -> attachments = attachments + attachment }
                .onFailure { permissionDenied = "The selected photo could not be opened." }
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { OmarRepository.attachmentFromUri(context.contentResolver, it, "FILE_PICKER") }
                .onSuccess { attachment -> attachments = attachments + attachment }
                .onFailure { permissionDenied = "The selected file could not be opened." }
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
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
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
            HomeHeader(serviceState, viewModel::startNewConversation, onOpenTasks, onOpenSettings)
        }
        item {
            AssistantInput(
                value = input,
                onValueChange = { input = it },
                attachments = attachments,
                onRemoveAttachment = { value -> deleteCameraPreview(value); attachments = attachments - value },
                busy = busy,
                onlineEnabled = viewModel.apiConfigured,
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
            WorkspaceSnapshot(
                tasks = tasks,
                localRecordCount = business.customers.size + business.leads.size + business.jobs.size + business.invoices.size,
                onOpenTasks = onOpenTasks,
                onOpenBusiness = onOpenBusiness,
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
        if (messages.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Recent conversation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${messages.size} saved", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(messages.takeLast(6), key = { it.id }) { message ->
                MessageCard(message, onReport = { reportMessage = message })
            }
        }
    }
}

private fun deleteCameraPreview(attachment: PendingAttachment) {
    if (attachment.source == "CAMERA" && attachment.uri.scheme == "file") {
        attachment.uri.path?.let { path -> runCatching { File(path).delete() } }
    }
}

@Composable
private fun HomeHeader(serviceState: ServiceConnectionState, onNewChat: () -> Unit, onTasks: () -> Unit, onSettings: () -> Unit) {
    val greeting = when (LocalTime.now().hour) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .36f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .16f)),
                shadowElevation = 3.dp,
            ) {
                OmarCompanion(Modifier.padding(3.dp), size = 62)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(greeting, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                Text("Omar AI", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Your AI companion", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onNewChat) { Icon(Icons.Rounded.AddComment, "Start new conversation") }
            IconButton(onClick = onTasks) { Icon(Icons.Rounded.TaskAlt, "Open Command Center") }
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Open settings") }
        }
        Surface(
            modifier = Modifier.padding(top = 12.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .62f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)),
        ) {
            Row(
                Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.OfflineBolt, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    when (serviceState) {
                        ServiceConnectionState.CONNECTED -> "Online service verified"
                        ServiceConnectionState.PENDING -> "Online service configured · verification pending"
                        ServiceConnectionState.FAILED -> "Online service check failed"
                        ServiceConnectionState.DISCONNECTED -> "Local mode"
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
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
            border = if (assistant) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f)) else null,
        ) {
            Column(Modifier.padding(15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (assistant) Icon(Icons.Rounded.SmartToy, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(if (assistant) (message.agent ?: "Omar AI") else "You", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
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
    onlineEnabled: Boolean,
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
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .16f)),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Rounded.SmartToy, null, Modifier.padding(9.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text("What do you want Omar AI to do?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Describe the outcome and add any useful context.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 128.dp),
                placeholder = { Text("For example: prepare a local plan for tomorrow’s customer…") },
                shape = RoundedCornerShape(18.dp),
                supportingText = {
                    Text(
                        if (value.text.isBlank()) "Text is saved only when you create the plan." else "${value.text.length} characters",
                    )
                },
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (attachments.isNotEmpty()) {
                Text(
                    "Attachment references and metadata are held only while this draft is open. Raw photos and files are not uploaded; media analysis remains unavailable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Text("Add context", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InputTool(Icons.Rounded.Mic, "Voice", "Use Android speech recognition", onMic, Modifier.weight(1f))
                    InputTool(Icons.Rounded.CameraAlt, "Camera", "Take a temporary preview", onCamera, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InputTool(Icons.Rounded.Image, "Photo", "Choose with Android picker", onPhoto, Modifier.weight(1f))
                    InputTool(Icons.Rounded.AttachFile, "File", "Choose a supported document", onFile, Modifier.weight(1f))
                }
            }
            Button(
                onClick = onSubmit,
                enabled = !busy && (value.text.isNotBlank() || attachments.isNotEmpty()),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 14.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(if (onlineEnabled) "  Asking Omar AI…" else "  Saving plan…")
                } else {
                    Icon(Icons.AutoMirrored.Rounded.Send, null)
                    Text(if (onlineEnabled) "  Ask Omar AI" else "  Create local plan")
                }
            }
            Text(if (onlineEnabled) "Online requests use a private guest session. Plans are not proof that external actions occurred." else "Local mode saves supported requests as local plans; no AI analysis or external action occurs.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
            Text("ASK → PLAN → APPROVE → ACT → VERIFY → REPORT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 7.dp))
        }
    }
}

@Composable
private fun InputTool(
    icon: ImageVector,
    label: String,
    accessibilityLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 70.dp)
            .semantics(mergeDescendants = true) { stateDescription = accessibilityLabel }
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)),
    ) {
        Column(
            Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 5.dp), maxLines = 1)
        }
    }
}

@Composable
private fun WorkspaceSnapshot(
    tasks: List<TaskEntity>,
    localRecordCount: Int,
    onOpenTasks: () -> Unit,
    onOpenBusiness: () -> Unit,
) {
    val activeCount = tasks.count { task -> task.sectionOrFailed() == TaskSection.ACTIVE }
    val approvalCount = tasks.count { task -> task.sectionOrFailed() == TaskSection.WAITING_FOR_APPROVAL }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Workspace snapshot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenTasks) { Text("View tasks"); Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(17.dp)) }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SnapshotCell("Active", activeCount.toString(), onOpenTasks, Modifier.weight(1f).fillMaxHeight())
            SnapshotCell("Approvals", approvalCount.toString(), onOpenTasks, Modifier.weight(1f).fillMaxHeight())
            SnapshotCell("Local records", localRecordCount.toString(), onOpenBusiness, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun SnapshotCell(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .86f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 13.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

private fun TaskEntity.sectionOrFailed(): TaskSection =
    TaskStatus.entries.firstOrNull { it.name == status }?.section() ?: TaskSection.FAILED

@Composable
private fun QuickActions(onBusiness: () -> Unit, onQuote: () -> Unit, onCompany: () -> Unit, onTasks: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text("Quick actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Start with a safe local draft or open a saved workspace.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        listOf(
            HomeAction(Icons.Rounded.BusinessCenter, "Business records", "CRM and entered metrics", onBusiness),
            HomeAction(Icons.Rounded.CameraAlt, "Prepare estimate", "Prefill a local draft", onQuote),
            HomeAction(Icons.Rounded.DomainAdd, "Plan a company", "Structure an idea locally", onCompany),
            HomeAction(Icons.Rounded.TaskAlt, "Command Center", "Review states and evidence", onTasks),
        ).chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { action ->
                    QuickActionCard(action, Modifier.weight(1f).fillMaxHeight())
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

private data class HomeAction(
    val icon: ImageVector,
    val title: String,
    val detail: String,
    val onClick: () -> Unit,
)

@Composable
private fun QuickActionCard(action: HomeAction, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .heightIn(min = 116.dp)
            .semantics(mergeDescendants = true) { stateDescription = action.detail }
            .clickable(role = Role.Button, onClick = action.onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)),
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(15.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(action.icon, null, Modifier.padding(9.dp).size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(action.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            Text(action.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun ReportOutputDialog(
    message: ChatMessageEntity,
    onDismiss: () -> Unit,
    onSubmit: (String, String?) -> Unit,
) {
    var category by rememberSaveable { mutableStateOf("INACCURATE") }
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report output") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose the closest reason. A local report record includes this output; it is submitted for review only if a connected service confirms receipt.")
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
