package com.narasimha.refire.core.util

import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object TimeUtils {

    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a")
    private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MMM d, h:mm a")

    fun formatDuration(duration: Duration): String {
        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60

        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "$hours hr"
            else -> "$minutes min"
        }
    }

    fun formatTime(dateTime: LocalDateTime): String {
        return dateTime.format(TIME_FORMATTER)
    }

    fun formatDateTime(dateTime: LocalDateTime): String {
        val now = LocalDateTime.now()

        return when {
            dateTime.toLocalDate() == now.toLocalDate() -> {
                "Today at ${formatTime(dateTime)}"
            }
            dateTime.toLocalDate() == now.toLocalDate().plusDays(1) -> {
                "Tomorrow at ${formatTime(dateTime)}"
            }
            else -> {
                dateTime.format(DATE_TIME_FORMATTER)
            }
        }
    }

    fun formatRelativeTime(until: LocalDateTime): String {
        val now = LocalDateTime.now()
        val minutes = ChronoUnit.MINUTES.between(now, until)
        val hours = ChronoUnit.HOURS.between(now, until)
        val days = ChronoUnit.DAYS.between(now, until)

        return when {
            minutes < 60 -> "in $minutes min"
            hours < 24 -> "in $hours hr"
            days == 1L -> "tomorrow"
            else -> "in $days days"
        }
    }
}
