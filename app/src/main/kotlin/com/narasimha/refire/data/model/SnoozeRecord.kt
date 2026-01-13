package com.narasimha.refire.data.model

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Represents an active snooze record.
 * Tracks what was snoozed and when it expires.
 */
data class SnoozeRecord(
    val id: String = UUID.randomUUID().toString(),
    val threadId: String,
    val notificationKey: String?,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String?,
    val snoozeEndTime: LocalDateTime,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val source: SnoozeSource,
    val shortcutId: String? = null,  // For deep-linking to conversations
    val groupKey: String? = null     // Fallback for deep-linking
) {
    /**
     * Check if this snooze has expired.
     */
    fun isExpired(): Boolean {
        return LocalDateTime.now().isAfter(snoozeEndTime)
    }

    /**
     * Get the remaining time until snooze expires.
     */
    fun timeRemaining(): Duration {
        val now = LocalDateTime.now()
        return if (now.isBefore(snoozeEndTime)) {
            Duration.between(now, snoozeEndTime)
        } else {
            Duration.ZERO
        }
    }

    /**
     * Format the remaining time as human-readable string.
     * Examples: "2h 15m", "45m", "Expired"
     */
    fun formattedTimeRemaining(): String {
        val remaining = timeRemaining()
        if (remaining.isZero || remaining.isNegative) {
            return "Expired"
        }

        val hours = remaining.toHours()
        val minutes = remaining.toMinutes() % 60

        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }

    /**
     * Format the end time for display.
     * Examples: "3:45 PM", "Tomorrow 9:00 AM"
     */
    fun formattedEndTime(): String {
        val now = LocalDateTime.now()
        val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

        return when {
            snoozeEndTime.toLocalDate() == now.toLocalDate() -> {
                snoozeEndTime.format(timeFormatter)
            }
            snoozeEndTime.toLocalDate() == now.toLocalDate().plusDays(1) -> {
                "Tomorrow ${snoozeEndTime.format(timeFormatter)}"
            }
            else -> {
                val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")
                snoozeEndTime.format(dateTimeFormatter)
            }
        }
    }

    /**
     * Get epoch millis for AlarmManager scheduling.
     */
    fun toEpochMillis(): Long {
        return snoozeEndTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    companion object {
        /**
         * Create a snooze record from a NotificationInfo.
         */
        fun fromNotification(
            notification: NotificationInfo,
            endTime: LocalDateTime
        ): SnoozeRecord {
            return SnoozeRecord(
                threadId = notification.getThreadIdentifier(),
                notificationKey = notification.key,
                packageName = notification.packageName,
                appName = notification.appName,
                title = notification.title ?: "Unknown",
                text = notification.text,
                snoozeEndTime = endTime,
                source = SnoozeSource.NOTIFICATION,
                shortcutId = notification.shortcutId,
                groupKey = notification.groupKey
            )
        }

        /**
         * Create a snooze record from shared content.
         */
        fun fromSharedContent(
            content: SharedContent,
            endTime: LocalDateTime
        ): SnoozeRecord {
            return SnoozeRecord(
                threadId = content.extractUrl() ?: content.text ?: UUID.randomUUID().toString(),
                notificationKey = null,
                packageName = content.sourcePackage ?: "unknown",
                appName = "Shared Content",
                title = content.getDisplayTitle(),
                text = content.text,
                snoozeEndTime = endTime,
                source = SnoozeSource.SHARE_SHEET
            )
        }
    }
}

/**
 * Where the snooze originated from.
 */
enum class SnoozeSource {
    NOTIFICATION,
    SHARE_SHEET
}
