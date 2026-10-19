package com.kaskita.lokal.domain

import com.kaskita.lokal.domain.TestData.CASH_ID
import com.kaskita.lokal.domain.TestData.income
import com.kaskita.lokal.domain.TestData.jakarta
import com.kaskita.lokal.domain.TestData.millis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId

class PeriodsTest {
    @Test
    fun monthBoundaryInclusiveStartExclusiveEnd() {
        val october = MonthRange.of(YearMonth.of(2026, 10), jakarta)
        val octStart = millis(2026, 10, 1, 0, 0)
        val novStart = millis(2026, 11, 1, 0, 0)
        assertEquals(octStart, october.startMillis)
        assertEquals(novStart, october.endExclusiveMillis)
        assertTrue(octStart in october)
        assertTrue(novStart - 1 in october)
        assertFalse(novStart in october)
        assertFalse(octStart - 1 in october)
    }

    @Test
    fun transactionExactlyAtNextMonthStartBelongsToNextMonth() {
        val atBoundary = income(1, 1_000, CASH_ID, date = millis(2026, 11, 1, 0, 0))
        val oct = Ledger.summary(listOf(atBoundary), MonthRange.of(YearMonth.of(2026, 10), jakarta))
        val nov = Ledger.summary(listOf(atBoundary), MonthRange.of(YearMonth.of(2026, 11), jakarta))
        assertEquals(0L, oct.income)
        assertEquals(1_000L, nov.income)
    }

    @Test
    fun decemberToJanuaryRollover() {
        val dec = MonthRange.of(YearMonth.of(2026, 12), jakarta)
        val jan = MonthRange.of(YearMonth.of(2027, 1), jakarta)
        assertEquals(jan.startMillis, dec.endExclusiveMillis)
        assertEquals(millis(2027, 1, 1, 0, 0), jan.startMillis)
    }

    @Test
    fun usesLocalZone() {
        val utc = ZoneId.of("UTC")
        val jkt = MonthRange.of(YearMonth.of(2026, 10), jakarta)
        val inUtc = MonthRange.of(YearMonth.of(2026, 10), utc)
        // Jakarta is UTC+7: its local midnight happens 7 hours earlier on the UTC timeline.
        assertEquals(7L * 60 * 60 * 1000, inUtc.startMillis - jkt.startMillis)
    }
}
