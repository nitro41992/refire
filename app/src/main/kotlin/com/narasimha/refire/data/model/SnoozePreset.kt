package com.narasimha.refire.data.model

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Represents a time preset for snoozing.
 * Sealed class with three variants: FixedDuration, TimeOfDay, Custom.
 */
sealed class SnoozePreset {
    abstract val displayLabel: String
    abstract fun calculateEndTime(from: LocalDateTime = LocalDateTime.now()): LocalDateTime

    /**
     * Fixed duration presets (e.g., 30 minutes, 1 hour)
     */
    data class FixedDuration(
        override val displayLabel: String,
        val duration: Duration
    ) : SnoozePreset() {
        override fun calculateEndTime(from: LocalDateTime): LocalDateTime {
            return from.plus(duration)
        }
    }

    /**
     * Time-of-day presets (e.g., "Tonight at 8pm", "Tomorrow at 9am")
     */
    data class TimeOfDay(
        override val displayLabel: String,
        val targetTime: LocalTime,
        val nextDayIfPassed: Boolean = true
    ) : SnoozePreset() {
        override fun calculateEndTime(from: LocalDateTime): LocalDateTime {
            var target = from.toLocalDate().atTime(targetTime)

            if (nextDayIfPassed && target.isBefore(from)) {
                target = target.plusDays(1)
            }

            return target
        }
    }

    /**
     * Custom time selected by user
     */
    data class Custom(
        val endTime: LocalDateTime
    ) : SnoozePreset() {
        override val displayLabel: String = "Custom"

        override fun calculateEndTime(from: LocalDateTime): LocalDateTime = endTime
    }

    companion object {
        /**
         * Default presets for the bottom sheet.
         */
        fun defaults(): List<SnoozePreset> = listOf(
            FixedDuration("30 min", Duration.ofMinutes(30)),
            FixedDuration("1 hour", Duration.ofHours(1)),
            FixedDuration("2 hours", Duration.ofHours(2)),
            FixedDuration("4 hours", Duration.ofHours(4)),
            TimeOfDay("Tonight", LocalTime.of(20, 0)),
            TimeOfDay("Tomorrow", LocalTime.of(9, 0), nextDayIfPassed = true)
        )
    }

    /**
     * Converts end time to epoch milliseconds for AlarmManager.
     */
    fun toEpochMillis(from: LocalDateTime = LocalDateTime.now()): Long {
        return calculateEndTime(from)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}
