package com.journai.journai.util

import kotlinx.datetime.*

object TimelineExtractor {
    // Very simple date extraction: look for yyyy-mm-dd or month names + day
    private val isoDate = Regex("\\b(20\\d{2})-([01]\\d)-([0-3]\\d)\\b")
    private val monthName = Regex("\\b(January|February|March|April|May|June|July|August|September|October|November|December)\\s+([0-3]?\\d)\\b", RegexOption.IGNORE_CASE)

    fun extractTimestamps(text: String, defaultZone: TimeZone = TimeZone.currentSystemDefault()): List<Instant> {
        if (text.isBlank()) return emptyList()
        val result = mutableListOf<Instant>()
        isoDate.findAll(text).forEach { m ->
            val y = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val d = m.groupValues[3].toInt()
            runCatching {
                val ld = LocalDate(y, mo, d)
                result.add(LocalDateTime(ld, LocalTime(0,0)).toInstant(defaultZone))
            }
        }
        monthName.findAll(text).forEach { m ->
            val mo = monthFromName(m.groupValues[1]) ?: return@forEach
            val d = m.groupValues[2].toIntOrNull() ?: return@forEach
            val y = Clock.System.now().toLocalDateTime(defaultZone).date.year
            runCatching {
                val ld = LocalDate(y, mo, d)
                result.add(LocalDateTime(ld, LocalTime(0,0)).toInstant(defaultZone))
            }
        }
        return result.distinct().sorted()
    }

    private fun monthFromName(name: String): Int? {
        return when (name.lowercase()) {
            "january" -> 1
            "february" -> 2
            "march" -> 3
            "april" -> 4
            "may" -> 5
            "june" -> 6
            "july" -> 7
            "august" -> 8
            "september" -> 9
            "october" -> 10
            "november" -> 11
            "december" -> 12
            else -> null
        }
    }
}


