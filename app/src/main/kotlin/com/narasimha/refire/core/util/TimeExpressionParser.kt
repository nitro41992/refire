package com.narasimha.refire.core.util

import com.narasimha.refire.data.model.SnoozePreset
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Result of parsing a time expression.
 */
data class ParseResult(
    val preset: SnoozePreset?,
    val displayText: String,
    val isValid: Boolean
)

/**
 * Parses natural time expressions into SnoozePreset.
 *
 * Supported formats:
 * - "@2:30" or "@2:30pm" → absolute time (TimeOfDay)
 * - "@12am" or "@9pm" → absolute time, hour only (TimeOfDay)
 * - "5h" or "5H" → duration in hours (FixedDuration)
 * - "30m" or "30M" → duration in minutes (FixedDuration)
 * - "2h30m" or "2H 30M" → duration in hours and minutes (FixedDuration)
 */
object TimeExpressionParser {

    private val timeFormatter12Hour = DateTimeFormatter.ofPattern("h:mm a")

    // Regex patterns
    private val absoluteTimeWithMinutes = Regex("""^@(\d{1,2}):(\d{2})\s*(am|pm)?$""", RegexOption.IGNORE_CASE)
    private val absoluteTimeHourOnly = Regex("""^@(\d{1,2})\s*(am|pm)?$""", RegexOption.IGNORE_CASE)
    private val durationHoursAndMinutes = Regex("""^(\d+)\s*h\s*(\d+)\s*m$""", RegexOption.IGNORE_CASE)
    private val durationHoursOnly = Regex("""^(\d+)\s*h$""", RegexOption.IGNORE_CASE)
    private val durationMinutesOnly = Regex("""^(\d+)\s*m$""", RegexOption.IGNORE_CASE)

    fun parse(input: String): ParseResult {
        val trimmed = input.trim()

        if (trimmed.isEmpty()) {
            return ParseResult(null, "", false)
        }

        // Try absolute time patterns (starts with @)
        if (trimmed.startsWith("@")) {
            return parseAbsoluteTime(trimmed)
        }

        // Try duration patterns
        return parseDuration(trimmed)
    }

    private fun parseAbsoluteTime(input: String): ParseResult {
        // Try @HH:MM or @H:MMam/pm
        absoluteTimeWithMinutes.matchEntire(input)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return invalidResult()
            val minute = match.groupValues[2].toIntOrNull() ?: return invalidResult()
            val amPm = match.groupValues[3].lowercase().ifEmpty { null }

            val adjustedHour = adjustHourForAmPm(hour, amPm) ?: return invalidResult()
            if (minute !in 0..59) return invalidResult()

            val targetTime = LocalTime.of(adjustedHour, minute)
            return createTimeOfDayResult(targetTime)
        }

        // Try @Ham/pm (hour only, e.g., @7, @6pm, @12am)
        absoluteTimeHourOnly.matchEntire(input)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return invalidResult()
            val amPm = match.groupValues[2].lowercase().ifEmpty { null }

            val adjustedHour = adjustHourForAmPm(hour, amPm) ?: return invalidResult()

            val targetTime = LocalTime.of(adjustedHour, 0)
            return createTimeOfDayResult(targetTime)
        }

        return invalidResult()
    }

    private fun parseDuration(input: String): ParseResult {
        // Try XhYm format
        durationHoursAndMinutes.matchEntire(input)?.let { match ->
            val hours = match.groupValues[1].toLongOrNull() ?: return invalidResult()
            val minutes = match.groupValues[2].toLongOrNull() ?: return invalidResult()

            if (hours < 0 || minutes < 0 || minutes > 59) return invalidResult()
            if (hours == 0L && minutes == 0L) return invalidResult()

            val duration = Duration.ofHours(hours).plusMinutes(minutes)
            return createDurationResult(hours.toInt(), minutes.toInt(), duration)
        }

        // Try Xh format
        durationHoursOnly.matchEntire(input)?.let { match ->
            val hours = match.groupValues[1].toLongOrNull() ?: return invalidResult()

            if (hours <= 0) return invalidResult()

            val duration = Duration.ofHours(hours)
            return createDurationResult(hours.toInt(), 0, duration)
        }

        // Try Xm format
        durationMinutesOnly.matchEntire(input)?.let { match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: return invalidResult()

            if (minutes <= 0) return invalidResult()

            val duration = Duration.ofMinutes(minutes)
            return createDurationResult(0, minutes.toInt(), duration)
        }

        return invalidResult()
    }

    private fun adjustHourForAmPm(hour: Int, amPm: String?): Int? {
        return when {
            amPm == null -> {
                // No am/pm specified - assume PM for hours 1-11, keep as-is for 12
                if (hour in 1..11) hour + 12
                else if (hour == 12) 12
                else if (hour in 13..23) hour
                else null
            }
            amPm == "am" -> {
                when {
                    hour == 12 -> 0  // 12am = midnight
                    hour in 1..11 -> hour
                    else -> null
                }
            }
            amPm == "pm" -> {
                when {
                    hour == 12 -> 12  // 12pm = noon
                    hour in 1..11 -> hour + 12
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun createTimeOfDayResult(targetTime: LocalTime): ParseResult {
        val now = LocalDateTime.now()
        val todayTarget = now.toLocalDate().atTime(targetTime)

        val dayLabel = if (todayTarget.isAfter(now)) "today" else "tomorrow"

        val preset = SnoozePreset.TimeOfDay(
            displayLabel = targetTime.format(timeFormatter12Hour),
            targetTime = targetTime,
            nextDayIfPassed = true
        )

        val displayText = "${targetTime.format(timeFormatter12Hour)} $dayLabel"
        return ParseResult(preset, displayText, true)
    }

    private fun createDurationResult(hours: Int, minutes: Int, duration: Duration): ParseResult {
        val label = when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }

        val preset = SnoozePreset.FixedDuration(label, duration)
        val displayText = "in $label"
        return ParseResult(preset, displayText, true)
    }

    private fun invalidResult(): ParseResult {
        return ParseResult(null, "", false)
    }
}
