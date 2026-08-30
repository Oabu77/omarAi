package com.darcloud.omarai.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darcloud.omarai.data.local.BusinessMetrics
import com.darcloud.omarai.data.local.InvoiceEntity
import com.darcloud.omarai.data.local.InvoiceStatuses
import com.darcloud.omarai.data.local.JobEntity
import com.darcloud.omarai.data.local.JobStatuses
import com.darcloud.omarai.data.local.LeadEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Calendar
import java.util.Currency

private enum class BusinessList { CUSTOMERS, LEADS, JOBS, INVOICES }

@Composable
fun BusinessScreen(viewModel: OmarViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.business.collectAsStateWithLifecycle()
    var list by rememberSaveable { mutableStateOf(BusinessList.CUSTOMERS) }
    var dialog by rememberSaveable { mutableStateOf<BusinessList?>(null) }

    dialog?.let { type ->
        when (type) {
            BusinessList.CUSTOMERS -> AddCustomerDialog({ dialog = null }) { name, phone, email -> viewModel.addCustomer(name, phone, email); dialog = null }
            BusinessList.LEADS -> AddLeadDialog({ dialog = null }) { title, amount -> viewModel.addLead(title, amount); dialog = null }
            BusinessList.JOBS -> AddJobDialog({ dialog = null }) { title, status, scheduledAt -> viewModel.addJob(title, status, scheduledAt); dialog = null }
            BusinessList.INVOICES -> AddInvoiceDialog({ dialog = null }) { label, total, paid, status -> viewModel.addInvoice(label, total, paid, status); dialog = null }
        }
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PageTitle("Business", "Summaries from user-entered records on this device", Icons.Rounded.BusinessCenter) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Recorded overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Calculated only from records you enter below.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill("LOCAL", MaterialTheme.colorScheme.primary)
            }
        }
        item { MetricsPanel(state.metrics) }
        item {
            if (state.metrics.attentionItems.isEmpty()) {
                HonestInfo("Business attention items will appear only when your saved records support them.", Modifier.padding(horizontal = 16.dp))
            } else {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = .32f)),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text("Needs attention", fontWeight = FontWeight.Bold)
                        }
                        state.metrics.attentionItems.forEach { item ->
                            Text("• $item", Modifier.padding(start = 3.dp, top = 7.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Local CRM", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Nothing here is sent, booked, invoiced, or paid externally.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Amounts and statuses are user-entered records, not verified bank or payment data.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                BusinessTabs(
                    selected = list,
                    counts = mapOf(
                        BusinessList.CUSTOMERS to state.customers.size,
                        BusinessList.LEADS to state.leads.size,
                        BusinessList.JOBS to state.jobs.size,
                        BusinessList.INVOICES to state.invoices.size,
                    ),
                ) { list = it }
                Button(onClick = { dialog = list }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 10.dp), shape = RoundedCornerShape(15.dp)) {
                    Icon(Icons.Rounded.Add, null); Text(" Add ${list.name.lowercase().dropLastWhile { it == 's' }}")
                }
            }
        }
        when (list) {
            BusinessList.CUSTOMERS -> {
                if (state.customers.isEmpty()) item { EmptyState("No customers yet", "Add a customer to create the first local CRM record.", Modifier.padding(horizontal = 16.dp)) }
                items(state.customers, key = { it.id }) { customer -> RecordCard(customer.name, listOfNotNull(customer.phone, customer.email).joinToString(" • ").ifBlank { "No contact details" }) }
            }
            BusinessList.LEADS -> {
                if (state.leads.isEmpty()) item { EmptyState("No leads yet", "Add a real lead; conversion stays blank until records exist.", Modifier.padding(horizontal = 16.dp)) }
                items(state.leads, key = { it.id }) { lead -> LeadCard(lead) { viewModel.updateLeadStatus(lead.id, it) } }
            }
            BusinessList.JOBS -> {
                if (state.jobs.isEmpty()) item { EmptyState("No jobs yet", "Jobs appear only after you add one.", Modifier.padding(horizontal = 16.dp)) }
                items(state.jobs, key = { it.id }) { job -> JobCard(job) }
            }
            BusinessList.INVOICES -> {
                if (state.invoices.isEmpty()) item { EmptyState("No invoices yet", "Drafts and payment records must be entered; Omar AI does not invent revenue.", Modifier.padding(horizontal = 16.dp)) }
                items(state.invoices, key = { it.id }) { invoice -> InvoiceCard(invoice) }
            }
        }
    }
}

@Composable
private fun MetricsPanel(metrics: BusinessMetrics) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Recorded today", money(metrics.todayRevenueMinor), Modifier.weight(1f))
            MetricCard("Recorded this month", money(metrics.monthlyRevenueMinor), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Outstanding", money(metrics.outstandingInvoicesMinor), Modifier.weight(1f))
            MetricCard("New leads", metrics.newLeadCount.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Recorded conversion", metrics.leadConversionRate?.let { "%.1f%%".format(it * 100) } ?: "Not enough data", Modifier.weight(1f))
            MetricCard("Average recorded payment", metrics.averageTicketMinor?.let(::money) ?: "Not enough data", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Jobs marked scheduled", metrics.scheduledJobCount.toString(), Modifier.weight(1f))
            MetricCard("Jobs marked completed", metrics.completedJobCount.toString(), Modifier.weight(1f))
        }
        Text("Customer acquisition cost: not tracked in this v1.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier) {
    Card(
        modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .42f)),
    ) {
        Column(Modifier.padding(15.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

@Composable
private fun BusinessTabs(selected: BusinessList, counts: Map<BusinessList, Int>, onSelected: (BusinessList) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(BusinessList.entries) { item ->
            val label = item.name.lowercase().replaceFirstChar { it.uppercase() }
            FilterChip(
                selected = selected == item,
                onClick = { onSelected(item) },
                label = { Text("$label ${counts[item] ?: 0}") },
                leadingIcon = if (selected == item) ({ Icon(Icons.Rounded.Check, null, Modifier.height(18.dp)) }) else null,
                modifier = Modifier.semantics { stateDescription = if (selected == item) "Selected" else "Not selected" },
            )
        }
    }
}

@Composable
private fun RecordCard(title: String, subtitle: String) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .36f)),
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.AutoMirrored.Rounded.ReceiptLong, null, Modifier.padding(9.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun LeadCard(lead: LeadEntity, update: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .36f)),
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Icon(Icons.AutoMirrored.Rounded.TrendingUp, null, tint = MaterialTheme.colorScheme.primary)
                Text(lead.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            lead.estimatedValueMinor?.let { Text("Estimated value entered: ${money(it)}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp)) }
            OutlinedButton(onClick = { menu = true }, modifier = Modifier.padding(top = 8.dp), shape = RoundedCornerShape(13.dp)) { Text("Recorded status: ${statusLabel(lead.status)}") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                OmarViewModel.leadStatuses.forEach { status -> DropdownMenuItem(text = { Text(statusLabel(status)) }, onClick = { menu = false; update(status) }) }
            }
        }
    }
}

@Composable
private fun JobCard(job: JobEntity) = RecordCard(
    job.title,
    "Recorded status: ${statusLabel(job.status)}" +
        (job.scheduledAtEpochMs?.let { " • ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(it)}" } ?: ""),
)

@Composable
private fun InvoiceCard(invoice: InvoiceEntity) = RecordCard(
    invoice.label,
    "Recorded status: ${statusLabel(invoice.status)}\nTotal ${money(invoice.totalMinor)} • Paid ${money(invoice.paidMinor)}",
)

@Composable
private fun AddCustomerDialog(onDismiss: () -> Unit, save: (String, String?, String?) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }; var phone by rememberSaveable { mutableStateOf("") }; var email by rememberSaveable { mutableStateOf("") }
    EntryDialog("Add local customer", "This creates a record on this device only.", onDismiss, name.isNotBlank(), { save(name, phone, email) }) {
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, label = { Text("Phone (optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("Email (optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AddLeadDialog(onDismiss: () -> Unit, save: (String, Long?) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }; var amount by rememberSaveable { mutableStateOf("") }
    val parsed = parseMoney(amount)
    EntryDialog("Add local lead", "Value is optional and is never treated as earned revenue.", onDismiss, title.isNotBlank() && (amount.isBlank() || parsed != null), { save(title, parsed) }) {
        OutlinedTextField(title, { title = it }, label = { Text("Lead title") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            amount,
            { amount = it },
            label = { Text("Estimated value USD (optional)") },
            isError = amount.isNotBlank() && parsed == null,
            supportingText = if (amount.isNotBlank() && parsed == null) ({ Text("Enter a non-negative amount, such as 125.00") }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AddJobDialog(onDismiss: () -> Unit, save: (String, String, Long?) -> Unit) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }; var status by rememberSaveable { mutableStateOf(JobStatuses.SCHEDULED) }
    var scheduledAt by rememberSaveable { mutableStateOf<Long?>(null) }
    val valid = title.isNotBlank() && (status != JobStatuses.SCHEDULED || scheduledAt != null)
    EntryDialog("Add local job", "This saves a record only; it does not book a provider or notify a customer.", onDismiss, valid, { save(title, status, scheduledAt.takeIf { status == JobStatuses.SCHEDULED }) }) {
        OutlinedTextField(title, { title = it }, label = { Text("Job title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        StatusButtons(OmarViewModel.jobStatuses, status) { status = it }
        if (status == JobStatuses.SCHEDULED) {
            OutlinedButton(
                onClick = {
                    val initial = Calendar.getInstance().apply { scheduledAt?.let { timeInMillis = it } }
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            scheduledAt = Calendar.getInstance().apply {
                                clear()
                                set(year, month, day, 12, 0, 0)
                            }.timeInMillis
                        },
                        initial.get(Calendar.YEAR),
                        initial.get(Calendar.MONTH),
                        initial.get(Calendar.DAY_OF_MONTH),
                    ).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.DateRange, null)
                Text(scheduledAt?.let { "  ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(it)}" } ?: "  Choose scheduled date")
            }
            if (scheduledAt == null) Text("Choose a date to save a scheduled job.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AddInvoiceDialog(onDismiss: () -> Unit, save: (String, Long, Long, String) -> Unit) {
    var label by rememberSaveable { mutableStateOf("") }; var total by rememberSaveable { mutableStateOf("") }; var paid by rememberSaveable { mutableStateOf("") }; var status by rememberSaveable { mutableStateOf(InvoiceStatuses.DRAFT) }
    val totalMinor = parseMoney(total); val paidMinor = parseMoney(paid.ifBlank { "0" })
    val valid = label.isNotBlank() && totalMinor != null && paidMinor != null && paidMinor <= totalMinor && (status != InvoiceStatuses.PAID || paidMinor == totalMinor)
    EntryDialog("Add local invoice record", "No invoice is sent and no payment is processed.", onDismiss, valid, { save(label, totalMinor!!, paidMinor!!, status) }) {
        OutlinedTextField(label, { label = it }, label = { Text("Invoice label") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(total, { total = it }, label = { Text("Total USD") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            paid,
            { paid = it },
            label = { Text("Amount already paid USD") },
            singleLine = true,
            isError = paidMinor != null && totalMinor != null && paidMinor > totalMinor,
            supportingText = if (paidMinor != null && totalMinor != null && paidMinor > totalMinor) ({ Text("Paid cannot exceed the total.") }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        StatusButtons(OmarViewModel.invoiceStatuses, status) { status = it }
        if (status == InvoiceStatuses.PAID && totalMinor != null && paidMinor != totalMinor) {
            Text("A Paid record requires paid amount to equal total.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun StatusButtons(values: List<String>, selected: String, onSelected: (String) -> Unit) {
    Column {
        Text("Recorded status", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelected(value) },
                    label = { Text(statusLabel(value)) },
                    leadingIcon = if (value == selected) ({ Icon(Icons.Rounded.Check, null, Modifier.height(18.dp)) }) else null,
                )
            }
        }
    }
}

@Composable
private fun EntryDialog(title: String, detail: String, onDismiss: () -> Unit, valid: Boolean, save: () -> Unit, fields: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                fields()
            }
        },
        confirmButton = { Button(onClick = save, enabled = valid) { Text("Save locally") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun statusLabel(value: String): String = value.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun parseMoney(text: String): Long? = try {
    BigDecimal(text.trim()).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact().takeIf { it >= 0L }
} catch (_: Exception) { null }

private fun money(minor: Long): String = NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance("USD") }.format(minor / 100.0)
