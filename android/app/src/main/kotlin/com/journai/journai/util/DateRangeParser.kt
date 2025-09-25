package com.journai.journai.util

import kotlinx.datetime.*

data class DateRange(val start: Instant, val end: Instant)

object DateRangeParser {
    private val yearRegex = Regex("\\b(19|20)\\d{2}\\b")
    private val pastDaysRegex = Regex("past\\s+(\\d{1,3})\\s+days?", RegexOption.IGNORE_CASE)
    private val lastYearRegex = Regex("last\\s+year", RegexOption.IGNORE_CASE)
    private val thisYearRegex = Regex("this\\s+year", RegexOption.IGNORE_CASE)

    fun parse(query: String, zone: TimeZone = TimeZone.currentSystemDefault()): DateRange? {
        val now = Clock.System.now()

        pastDaysRegex.find(query)?.groups?.get(1)?.value?.toIntOrNull()?.let { days ->
            val end = now
            val start = end.minus(days.toLong() * 24L * 60L * 60L, DateTimeUnit.SECOND)
            return DateRange(start, end)
        }

        if (lastYearRegex.containsMatchIn(query)) {
            val year = now.toLocalDateTime(zone).date.year - 1
            return yearRange(year, zone)
        }
        if (thisYearRegex.containsMatchIn(query)) {
            val year = now.toLocalDateTime(zone).date.year
            return yearRange(year, zone)
        }

        val years = yearRegex.findAll(query).map { it.value.toInt() }.toList()
        if (years.isNotEmpty()) {
            // If multiple years present, take the first for MVP
            return yearRange(years.first(), zone)
        }
        return null
    }

    private fun yearRange(year: Int, zone: TimeZone): DateRange? {
        return try {
            val startDate = LocalDate(year, 1, 1)
            val endDate = LocalDate(year, 12, 31)
            val start = LocalDateTime(startDate, LocalTime(0, 0)).toInstant(zone)
            val end = LocalDateTime(endDate, LocalTime(23, 59, 59)).toInstant(zone)
            DateRange(start, end)
        } catch (_: Throwable) { null }
    }
}


