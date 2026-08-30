package com.darcloud.omarai

import com.darcloud.omarai.data.local.BusinessMetricsCalculator
import com.darcloud.omarai.data.local.InvoiceEntity
import com.darcloud.omarai.data.local.InvoiceStatuses
import com.darcloud.omarai.data.local.JobEntity
import com.darcloud.omarai.data.local.JobStatuses
import com.darcloud.omarai.data.local.LeadEntity
import com.darcloud.omarai.data.local.LeadStatuses
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BusinessMetricsTest {
    @Test fun emptyRecordsProduceZeroOrHonestUnknowns() {
        val result = BusinessMetricsCalculator.calculate(emptyList(), emptyList(), emptyList())
        assertEquals(0L, result.todayRevenueMinor)
        assertEquals(0L, result.monthlyRevenueMinor)
        assertNull(result.leadConversionRate)
        assertNull(result.averageTicketMinor)
    }

    @Test fun revenueAndConversionUseOnlySuppliedRecords() {
        val now = LocalDateTime.of(2026, 8, 30, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val leads = listOf(
            LeadEntity("1", null, "Won", null, LeadStatuses.WON, 10000, "USD", now),
            LeadEntity("2", null, "New", null, LeadStatuses.NEW, null, "USD", now),
        )
        val invoices = listOf(
            InvoiceEntity("i", null, "Paid", InvoiceStatuses.PAID, 12_500, 12_500, "USD", now, now),
            InvoiceEntity("o", null, "Open", InvoiceStatuses.SENT, 20_000, 5_000, "USD", now, now),
        )
        val jobs = listOf(JobEntity("j", null, "Job", JobStatuses.SCHEDULED, now, null, null, "USD", now))
        val result = BusinessMetricsCalculator.calculate(leads, jobs, invoices, now, ZoneOffset.UTC)
        assertEquals(17_500L, result.todayRevenueMinor)
        assertEquals(15_000L, result.outstandingInvoicesMinor)
        assertEquals(0.5, result.leadConversionRate!!, 0.0001)
        assertEquals(1, result.scheduledJobCount)
    }

    @Test fun revenueHonorsSelectedTimeZoneAndCalendarBoundaries() {
        val zone = ZoneId.of("America/Los_Angeles")
        val now = LocalDateTime.of(2026, 9, 1, 0, 30).atZone(zone).toInstant().toEpochMilli()
        val sameLocalDay = LocalDateTime.of(2026, 9, 1, 0, 5).atZone(zone).toInstant().toEpochMilli()
        val previousLocalDay = LocalDateTime.of(2026, 8, 31, 23, 55).atZone(zone).toInstant().toEpochMilli()
        val invoices = listOf(
            InvoiceEntity("today", null, "Today", InvoiceStatuses.PAID, 2_500, 2_500, "USD", now, sameLocalDay),
            InvoiceEntity("yesterday", null, "Yesterday", InvoiceStatuses.PAID, 7_500, 7_500, "USD", now, previousLocalDay),
        )
        val result = BusinessMetricsCalculator.calculate(emptyList(), emptyList(), invoices, now, zone)
        assertEquals(2_500L, result.todayRevenueMinor)
        assertEquals(2_500L, result.monthlyRevenueMinor)
    }

    @Test fun outstandingBalanceNeverBecomesNegativeAndUsesOnlyOpenStatuses() {
        val now = 1_800_000_000_000L
        val invoices = listOf(
            InvoiceEntity("sent", null, "Sent", InvoiceStatuses.SENT, 10_000, 2_500, "USD", now, now),
            InvoiceEntity("overpaid", null, "Overpaid", InvoiceStatuses.PARTIAL, 1_000, 2_000, "USD", now, now),
            InvoiceEntity("draft", null, "Draft", InvoiceStatuses.DRAFT, 90_000, 0, "USD", now, null),
            InvoiceEntity("void", null, "Void", InvoiceStatuses.VOID, 80_000, 0, "USD", now, null),
        )
        val result = BusinessMetricsCalculator.calculate(emptyList(), emptyList(), invoices, now, ZoneOffset.UTC)
        assertEquals(7_500L, result.outstandingInvoicesMinor)
        assertEquals(listOf("1 invoice has an outstanding balance"), result.attentionItems)
    }

    @Test fun attentionItemsAndCountsComeOnlyFromMatchingLocalStatuses() {
        val now = 1_800_000_000_000L
        val leads = listOf(
            LeadEntity("new", null, "New", null, LeadStatuses.NEW, null, "USD", now),
            LeadEntity("lost", null, "Lost", null, LeadStatuses.LOST, null, "USD", now),
        )
        val jobs = listOf(
            JobEntity("needs-date", null, "Needs date", JobStatuses.SCHEDULED, null, null, null, "USD", now),
            JobEntity("dated", null, "Dated", JobStatuses.SCHEDULED, now, null, null, "USD", now),
            JobEntity("done", null, "Done", JobStatuses.COMPLETED, now, now, null, "USD", now),
        )
        val result = BusinessMetricsCalculator.calculate(leads, jobs, emptyList(), now, ZoneOffset.UTC)
        assertEquals(1, result.newLeadCount)
        assertEquals(2, result.scheduledJobCount)
        assertEquals(1, result.completedJobCount)
        assertEquals(listOf("1 new lead needs review", "1 job needs a date"), result.attentionItems)
    }
}
