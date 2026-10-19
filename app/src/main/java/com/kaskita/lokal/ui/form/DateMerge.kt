package com.kaskita.lokal.ui.form

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Material's DatePicker reports a day as UTC midnight; turn that into the same local day at [existing]'s time of day. */
fun mergeDatePreservingTime(existing: Long, pickedUtcMillis: Long, zone: ZoneId): Long {
    val pickedDay = Instant.ofEpochMilli(pickedUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val time = Instant.ofEpochMilli(existing).atZone(zone).toLocalTime()
    return pickedDay.atTime(time).atZone(zone).toInstant().toEpochMilli()
}

/** The value to hand to the DatePicker so it opens on [millis]'s local day. */
fun datePickerInitialMillis(millis: Long, zone: ZoneId): Long {
    val localDay: LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    return localDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
