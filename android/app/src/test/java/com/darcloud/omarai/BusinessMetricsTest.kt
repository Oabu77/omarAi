package com.darcloud.omarai

import com.darcloud.omarai.data.local.BusinessMetricsCalculator
import com.darcloud.omarai.data.local.InvoiceEntity
import com.darcloud.omarai.data.local.InvoiceStatuses
import com.darcloud.omarai.data.local.JobEntity
import com.darcloud.omarai.data.local.JobStatuses
import com.darcloud.omarai.data.local.LeadEntity
import com.darcloud.omarai.data.local.LeadStatuses
import java.time.LocalDateTime
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
}
