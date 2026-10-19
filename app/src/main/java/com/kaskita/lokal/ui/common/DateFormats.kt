package com.kaskita.lokal.ui.common

import androidx.compose.foundation.layout.WindowInsets
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val indonesian: Locale = Locale.forLanguageTag("id-ID")
private val longDate = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", indonesian)
private val shortDate = DateTimeFormatter.ofPattern("d MMM yyyy", indonesian)
private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", indonesian)
private val monthYearShort = DateTimeFormatter.ofPattern("MMM yyyy", indonesian)
private val monthShort = DateTimeFormatter.ofPattern("MMM", indonesian)
private val dayMonth = DateTimeFormatter.ofPattern("d MMM", indonesian)
private val dateTime = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", indonesian)

fun formatDateLong(millis: Long, zone: ZoneId): String = Instant.ofEpochMilli(millis).atZone(zone).format(longDate)
fun formatDateTime(millis: Long, zone: ZoneId): String = Instant.ofEpochMilli(millis).atZone(zone).format(dateTime)
fun formatDateShort(millis: Long, zone: ZoneId): String = Instant.ofEpochMilli(millis).atZone(zone).format(shortDate)
fun formatDate(day: LocalDate): String = day.format(shortDate)
fun formatDayMonth(day: LocalDate): String = day.format(dayMonth)
fun formatMonthYear(month: YearMonth): String = month.format(monthYear)
fun formatMonthYearShort(month: YearMonth): String = month.format(monthYearShort)
fun formatMonthShort(month: YearMonth): String = month.format(monthShort)

/** Screens own their insets (TopAppBar pads the status bar; bottom content pads the navigation bar itself). */
val NoInsets = WindowInsets(0, 0, 0, 0)
