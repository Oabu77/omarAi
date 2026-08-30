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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import java.text.NumberFormat
import java.util.Currency

private enum class BusinessList { CUSTOMERS, LEADS, JOBS, INVOICES }

@Composable
fun BusinessScreen(viewModel: OmarViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.business.collectAsStateWithLifecycle()
    var list by remember { mutableStateOf(BusinessList.CUSTOMERS) }
    var dialog by remember { mutableStateOf<BusinessList?>(null) }

    dialog?.let { type ->
        when (type) {
            BusinessList.CUSTOMERS -> AddCustomerDialog({ dialog = null }) { name, phone, email -> viewModel.addCustomer(name, phone, email); dialog = null }
            BusinessList.LEADS -> AddLeadDialog({ dialog = null }) { title, amount -> viewModel.addLead(title, amount); dialog = null }
            BusinessList.JOBS -> AddJobDialog({ dialog = null }) { title, status -> viewModel.addJob(title, status, System.currentTimeMillis().takeIf { status == JobStatuses.SCHEDULED }); dialog = null }
            BusinessList.INVOICES -> AddInvoiceDialog({ dialog = null }) { label, total, paid, status -> viewModel.addInvoice(label, total, paid, status); dialog = null }
        }
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PageTitle("Business", "Real metrics from records saved on this device", Icons.Rounded.BusinessCenter) }
        item { MetricsPanel(state.metrics) }
        item {
            if (state.metrics.attentionItems.isEmpty()) {
                HonestInfo("Business attention items will appear only when your saved records support them.", Modifier.padding(horizontal = 16.dp))
            } else {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Needs attention", fontWeight = FontWeight.Bold)
                        state.metrics.attentionItems.forEach { Text("• $it", Modifier.padding(top = 5.dp)) }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Local CRM", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Nothing here is sent, booked, invoiced, or paid externally.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                BusinessTabs(list) { list = it }
                Button(onClick = { dialog = list }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
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
            MetricCard("Today’s revenue", money(metrics.todayRevenueMinor), Modifier.weight(1f))
            MetricCard("Monthly revenue", money(metrics.monthlyRevenueMinor), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Outstanding", money(metrics.outstandingInvoicesMinor), Modifier.weight(1f))
            MetricCard("New leads", metrics.newLeadCount.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Conversion", metrics.leadConversionRate?.let { "%.1f%%".format(it * 100) } ?: "Not enough data", Modifier.weight(1f))
            MetricCard("Average ticket", metrics.averageTicketMinor?.let(::money) ?: "Not enough data", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Jobs scheduled", metrics.scheduledJobCount.toString(), Modifier.weight(1f))
            MetricCard("Jobs completed", metrics.completedJobCount.toString(), Modifier.weight(1f))
        }
        Text("Customer acquisition cost: not tracked in this v1.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun BusinessTabs(selected: BusinessList, onSelected: (BusinessList) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BusinessList.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    OutlinedButton(onClick = { onSelected(item) }, modifier = Modifier.weight(1f)) {
                        val label = item.name.lowercase().replaceFirstChar { it.uppercase() }
                        Text(if (selected == item) "✓ $label" else label)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordCard(title: String, subtitle: String) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(15.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun LeadCard(lead: LeadEntity, update: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(15.dp)) {
            Text(lead.title, fontWeight = FontWeight.SemiBold)
            lead.estimatedValueMinor?.let { Text("Estimated value entered: ${money(it)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedButton(onClick = { menu = true }, modifier = Modifier.padding(top = 8.dp)) { Text(lead.status.replace('_', ' ')) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                OmarViewModel.leadStatuses.forEach { status -> DropdownMenuItem(text = { Text(status) }, onClick = { menu = false; update(status) }) }
            }
        }
    }
}

@Composable private fun JobCard(job: JobEntity) = RecordCard(job.title, "Status: ${job.status.replace('_', ' ')}${if (job.scheduledAtEpochMs != null) " • Date saved" else ""}")

@Composable
private fun InvoiceCard(invoice: InvoiceEntity) = RecordCard(
    invoice.label,
    "${invoice.status} • Total ${money(invoice.totalMinor)} • Paid ${money(invoice.paidMinor)}",
)

@Composable
private fun AddCustomerDialog(onDismiss: () -> Unit, save: (String, String?, String?) -> Unit) {
    var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }
    EntryDialog("Add local customer", "This creates a record on this device only.", onDismiss, name.isNotBlank(), { save(name, phone, email) }) {
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, label = { Text("Phone (optional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("Email (optional)") }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AddLeadDialog(onDismiss: () -> Unit, save: (String, Long?) -> Unit) {
    var title by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }
    val parsed = parseMoney(amount)
    EntryDialog("Add local lead", "Value is optional and is never treated as earned revenue.", onDismiss, title.isNotBlank() && (amount.isBlank() || parsed != null), { save(title, parsed) }) {
        OutlinedTextField(title, { title = it }, label = { Text("Lead title") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(amount, { amount = it }, label = { Text("Estimated value USD (optional)") }, isError = amount.isNotBlank() && parsed == null, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AddJobDialog(onDismiss: () -> Unit, save: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }; var status by remember { mutableStateOf(JobStatuses.SCHEDULED) }
    EntryDialog("Add local job", "This does not book a provider or notify a customer.", onDismiss, title.isNotBlank(), { save(title, status) }) {
        OutlinedTextField(title, { title = it }, label = { Text("Job title") }, modifier = Modifier.fillMaxWidth())
        StatusButtons(OmarViewModel.jobStatuses, status) { status = it }
    }
}

@Composable
private fun AddInvoiceDialog(onDismiss: () -> Unit, save: (String, Long, Long, String) -> Unit) {
    var label by remember { mutableStateOf("") }; var total by remember { mutableStateOf("") }; var paid by remember { mutableStateOf("") }; var status by remember { mutableStateOf(InvoiceStatuses.DRAFT) }
    val totalMinor = parseMoney(total); val paidMinor = parseMoney(paid.ifBlank { "0" })
    val valid = label.isNotBlank() && totalMinor != null && paidMinor != null && paidMinor <= totalMinor
    EntryDialog("Add local invoice record", "No invoice is sent and no payment is processed.", onDismiss, valid, { save(label, totalMinor!!, paidMinor!!, status) }) {
        OutlinedTextField(label, { label = it }, label = { Text("Invoice label") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(total, { total = it }, label = { Text("Total USD") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(paid, { paid = it }, label = { Text("Amount already paid USD") }, modifier = Modifier.fillMaxWidth())
        StatusButtons(OmarViewModel.invoiceStatuses, status) { status = it }
    }
}

@Composable
private fun StatusButtons(values: List<String>, selected: String, onSelected: (String) -> Unit) {
    Column { values.forEach { value -> TextButton(onClick = { onSelected(value) }) { Text(if (value == selected) "✓ $value" else value) } } }
}

@Composable
private fun EntryDialog(title: String, detail: String, onDismiss: () -> Unit, valid: Boolean, save: () -> Unit, fields: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant); fields() } },
        confirmButton = { Button(onClick = save, enabled = valid) { Text("Save locally") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun parseMoney(text: String): Long? = try {
    BigDecimal(text.trim()).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact().takeIf { it >= 0L }
} catch (_: Exception) { null }

private fun money(minor: Long): String = NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance("USD") }.format(minor / 100.0)
