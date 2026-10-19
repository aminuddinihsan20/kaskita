package com.kaskita.lokal.ui.form

import com.kaskita.lokal.domain.TestData
import com.kaskita.lokal.domain.TestData.jakarta
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DateMergeTest {
    private fun pickerUtcMidnight(y: Int, m: Int, d: Int) =
        LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun pickedDateKeepsTheOriginalLocalTimeOfDay() {
        val existing = TestData.millis(2026, 10, 3, 14, 30)
        val merged = mergeDatePreservingTime(existing, pickerUtcMidnight(2026, 10, 20), jakarta)
        assertEquals(TestData.millis(2026, 10, 20, 14, 30), merged)
    }

    @Test
    fun lateEveningStaysOnThePickedLocalDay() {
        val existing = TestData.millis(2026, 10, 3, 23, 59)
        val merged = mergeDatePreservingTime(existing, pickerUtcMidnight(2026, 12, 31), jakarta)
        assertEquals(TestData.millis(2026, 12, 31, 23, 59), merged)
    }

    @Test
    fun datePickerInitialValueIsTheLocalDateAtUtcMidnight() {
        val existing = TestData.millis(2026, 10, 3, 0, 5) // 00:05 Jakarta is still Oct 2 in UTC
        assertEquals(pickerUtcMidnight(2026, 10, 3), datePickerInitialMillis(existing, jakarta))
    }
}
