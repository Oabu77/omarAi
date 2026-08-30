package com.darcloud.omarai.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darcloud.omarai.data.local.TaskEntity
import com.darcloud.omarai.data.local.TaskSection
import com.darcloud.omarai.data.local.TaskStatus
import com.darcloud.omarai.data.local.section
import java.text.DateFormat
import java.util.Date

@Composable
fun TaskCenterScreen(viewModel: OmarViewModel, modifier: Modifier = Modifier) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var section by remember { mutableStateOf(TaskSection.ACTIVE) }
    var selected by remember { mutableStateOf<TaskEntity?>(null) }
    var approve by remember { mutableStateOf<TaskEntity?>(null) }
    var cancel by remember { mutableStateOf<TaskEntity?>(null) }
    val filtered = tasks.filter { statusOf(it).section() == section }

    selected?.let { task -> TaskDetailDialog(task, { selected = null }, { viewModel.refreshTask(task.id) }) }
    approve?.let { task ->
        AlertDialog(
            onDismissRequest = { approve = null },
            title = { Text("Approve this plan?") },
            text = { Column { Text(task.approvalPrompt ?: "Approve the actions shown in this task?"); Spacer(Modifier.height(10.dp)); Text("Approval does not mean completion. Omar AI will still verify and report the resulting state.", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            confirmButton = { Button(enabled = !busy, onClick = { viewModel.approve(task.id); approve = null }) { Text("Approve") } },
            dismissButton = { TextButton(onClick = { approve = null }) { Text("Not now") } },
        )
    }
    cancel?.let { task ->
        AlertDialog(
            onDismissRequest = { cancel = null },
            title = { Text("Cancel task?") },
            text = { Text("Omar AI will request cancellation. If a remote service does not confirm it, the previous state remains unchanged.") },
            confirmButton = { Button(enabled = !busy, onClick = { viewModel.cancel(task.id); cancel = null }) { Text("Cancel task") } },
            dismissButton = { TextButton(onClick = { cancel = null }) { Text("Keep task") } },
        )
    }

    Column(modifier.fillMaxSize()) {
        PageTitle("Command Center", "Exact task state, approvals, evidence, and errors", Icons.Rounded.TaskAlt)
        HonestInfo("Prepared ≠ submitted. Submitted ≠ completed. Completed requires backend verification evidence.", Modifier.padding(horizontal = 16.dp))
        TaskTabs(section) { section = it }
        if (filtered.isEmpty()) {
            EmptyState(
                title = "No ${sectionTitle(section).lowercase()} tasks",
                detail = "Tasks appear here only after a real request creates this state.",
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.id }) { task ->
                    TaskCard(
                        task,
                        onDetails = { selected = task },
                        onApprove = { approve = task },
                        onCancel = { cancel = task },
                        busy = busy,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskTabs(selected: TaskSection, onSelected: (TaskSection) -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TaskSection.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { section ->
                    OutlinedButton(onClick = { onSelected(section) }, modifier = Modifier.weight(1f)) {
                        Text(if (selected == section) "✓ ${sectionTitle(section)}" else sectionTitle(section), maxLines = 1)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun sectionTitle(section: TaskSection): String = when (section) {
    TaskSection.ACTIVE -> "Active"
    TaskSection.WAITING_FOR_APPROVAL -> "Waiting for Approval"
    TaskSection.SCHEDULED -> "Scheduled"
    TaskSection.COMPLETED -> "Completed"
    TaskSection.FAILED -> "Failed"
}

@Composable
private fun TaskCard(task: TaskEntity, onDetails: () -> Unit, onApprove: () -> Unit, onCancel: () -> Unit, busy: Boolean) {
    val status = statusOf(task)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(task.title, fontWeight = FontWeight.Bold)
                    Text(task.agent, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill(status.name, statusColor(status))
            }
            Text("Started ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(task.startedAtEpochMs))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            task.result?.let { Text("Result: $it", Modifier.padding(top = 8.dp)) }
            task.error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDetails) { Text("Details") }
                if (status == TaskStatus.AWAITING_APPROVAL) Button(enabled = !busy, onClick = onApprove) { Text("Review & approve") }
                if (task.cancellable && !status.isTerminal) OutlinedButton(enabled = !busy, onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun TaskDetailDialog(task: TaskEntity, onDismiss: () -> Unit, onRefresh: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(task.title) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Detail("Agent", task.agent) }
                item { Detail("Status", task.status.replace('_', ' ')) }
                item { Detail("Started", DateFormat.getDateTimeInstance().format(Date(task.startedAtEpochMs))) }
                item { Detail("Actions performed", task.actionsPerformed.ifBlank { "None reported" }) }
                item { Detail("Permissions used", task.permissionsUsed.ifBlank { "None reported" }) }
                item { Detail("Result", task.result ?: "No result reported") }
                item { Detail("Error", task.error ?: "None reported") }
                item { Detail("Verification evidence", task.providerEvidenceId ?: "None") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { TextButton(enabled = task.remoteId != null, onClick = onRefresh) { Icon(Icons.Rounded.Refresh, null); Text(" Refresh") } },
    )
}

@Composable private fun Detail(label: String, value: String) { Column { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); Text(value) } }

private fun statusOf(task: TaskEntity): TaskStatus = TaskStatus.entries.firstOrNull { it.name == task.status } ?: TaskStatus.FAILED

private fun statusColor(status: TaskStatus): Color = when (status) {
    TaskStatus.COMPLETED -> OmarTeal
    TaskStatus.FAILED, TaskStatus.CANCELLED -> OmarRed
    TaskStatus.AWAITING_APPROVAL, TaskStatus.PARTIAL -> OmarAmber
    else -> OmarBlue
}
