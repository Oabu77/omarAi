package com.darcloud.omarai.data.local

import java.time.Instant
import java.time.ZoneId

data class BusinessMetrics(
    val todayRevenueMinor: Long,
    val monthlyRevenueMinor: Long,
    val outstandingInvoicesMinor: Long,
    val newLeadCount: Int,
    val leadConversionRate: Double?,
    val scheduledJobCount: Int,
    val completedJobCount: Int,
    val averageTicketMinor: Long?,
    val attentionItems: List<String>,
)

object BusinessMetricsCalculator {
    fun calculate(
        leads: List<LeadEntity>,
        jobs: List<JobEntity>,
        invoices: List<InvoiceEntity>,
        nowEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): BusinessMetrics {
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
        val month = today.year to today.monthValue

        val todayRevenue = invoices.sumOf { invoice ->
            val paidDate = invoice.paymentReceivedAtEpochMs
                ?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
            if (paidDate == today) invoice.paidMinor else 0L
        }
        val monthlyRevenue = invoices.sumOf { invoice ->
            val paidDate = invoice.paymentReceivedAtEpochMs
                ?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
            if (paidDate != null && paidDate.year == month.first && paidDate.monthValue == month.second) {
                invoice.paidMinor
            } else 0L
        }
        val outstanding = invoices
            .filter { it.status in setOf(InvoiceStatuses.SENT, InvoiceStatuses.PARTIAL) }
            .sumOf { (it.totalMinor - it.paidMinor).coerceAtLeast(0L) }
        val won = leads.count { it.status == LeadStatuses.WON }
        val conversion = leads.takeIf { it.isNotEmpty() }
            ?.let { won.toDouble() / it.size.toDouble() }
        val paid = invoices.filter { it.paidMinor > 0L }
        val average = paid.takeIf { it.isNotEmpty() }?.map { it.paidMinor }?.average()?.toLong()

        val attention = buildList {
            val untouched = leads.count { it.status == LeadStatuses.NEW }
            if (untouched > 0) add("$untouched new lead${if (untouched == 1) " needs" else "s need"} review")
            val overdueCandidateCount = invoices.count {
                it.status in setOf(InvoiceStatuses.SENT, InvoiceStatuses.PARTIAL) &&
                    it.totalMinor > it.paidMinor
            }
            if (overdueCandidateCount > 0) {
                add("$overdueCandidateCount invoice${if (overdueCandidateCount == 1) " has" else "s have"} an outstanding balance")
            }
            val unscheduled = jobs.count { it.status == JobStatuses.SCHEDULED && it.scheduledAtEpochMs == null }
            if (unscheduled > 0) add("$unscheduled job${if (unscheduled == 1) " needs" else "s need"} a date")
        }

        return BusinessMetrics(
            todayRevenueMinor = todayRevenue,
            monthlyRevenueMinor = monthlyRevenue,
            outstandingInvoicesMinor = outstanding,
            newLeadCount = leads.count { it.status == LeadStatuses.NEW },
            leadConversionRate = conversion,
            scheduledJobCount = jobs.count { it.status == JobStatuses.SCHEDULED },
            completedJobCount = jobs.count { it.status == JobStatuses.COMPLETED },
            averageTicketMinor = average,
            attentionItems = attention,
        )
    }
}
