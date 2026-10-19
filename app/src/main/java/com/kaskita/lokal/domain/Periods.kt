package com.kaskita.lokal.domain

import java.time.YearMonth
import java.time.ZoneId

/** Start of month inclusive, start of next month exclusive, both in epoch millis for a given local zone. */
class MonthRange(val startMillis: Long, val endExclusiveMillis: Long) {
    operator fun contains(millis: Long): Boolean = millis >= startMillis && millis < endExclusiveMillis

    companion object {
        fun of(month: YearMonth, zone: ZoneId): MonthRange = MonthRange(
            startMillis = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            endExclusiveMillis = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }
}
