package com.narasimha.refire.data.model

import android.content.Context
import com.narasimha.refire.core.util.AppNameResolver
import com.narasimha.refire.core.util.UrlUtils
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Represents a snooze record.
 * Tracks what was snoozed, when it expires, and its lifecycle status.
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
    val groupKey: String? = null,    // Fallback for deep-linking
    val contentType: String? = null, // "URL", "PLAIN_TEXT", "IMAGE", null for notifications
    val messages: List<MessageData> = emptyList(),  // Extracted messages from grouped notifications
    val status: SnoozeStatus = SnoozeStatus.ACTIVE,  // Lifecycle status
    val suppressedCount: Int = 0  // Count of messages suppressed after snooze creation
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
     * Examples: "in 2h 15m", "in 45m", "Expired"
     */
    fun formattedTimeRemaining(): String {
        val remaining = timeRemaining()
        if (remaining.isZero || remaining.isNegative) {
            return "Expired"
        }

        val hours = remaining.toHours()
        val minutes = remaining.toMinutes() % 60

        return when {
            hours > 0 && minutes > 0 -> "in ${hours}h ${minutes}m"
            hours > 0 -> "in ${hours}h"
            else -> "in ${minutes}m"
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

    /**
     * Get domain from URL for display (only for shared URLs).
     */
    fun getDomain(): String? {
        if (source != SnoozeSource.SHARE_SHEET) return null
        if (contentType != "URL") return null
        return text?.let { UrlUtils.extractDomain(it) }
    }

    /**
     * Check if this is a shared URL (vs notification or plain text).
     */
    fun isSharedUrl(): Boolean {
        return source == SnoozeSource.SHARE_SHEET && contentType == "URL"
    }

    /**
     * Get display text for the content area (avoids redundancy).
     */
    fun getDisplayText(): String? {
        return when {
            // For shared URLs, only show full URL if title doesn't contain it
            isSharedUrl() -> {
                val domain = getDomain()
                // If title already shows domain, don't repeat URL
                if (domain != null && title.contains(domain, ignoreCase = true)) {
                    null  // Don't show text (avoid redundancy)
                } else {
                    text  // Show full URL
                }
            }

            // For notifications and plain text, always show text
            else -> text
        }
    }

    /**
     * Returns the best available text content for display.
     * Prioritizes extracted messages over summary text.
     */
    fun getBestTextContent(): String {
        // If we have extracted messages, show them as a summary
        if (messages.isNotEmpty()) {
            return messages.joinToString("\n") { it.text }
        }

        // Fallback to original text
        return text ?: ""
    }

    /**
     * Format the time since this snooze was re-fired (for history display).
     * Uses snoozeEndTime as the re-fire time.
     * Examples: "5m ago", "2h ago", "Yesterday", "Jan 5"
     */
    fun formattedTimeSinceRefired(): String {
        val now = LocalDateTime.now()
        val elapsed = Duration.between(snoozeEndTime, now)

        return when {
            elapsed.toMinutes() < 1 -> "Just now"
            elapsed.toMinutes() < 60 -> "${elapsed.toMinutes()}m ago"
            elapsed.toHours() < 24 -> "${elapsed.toHours()}h ago"
            elapsed.toDays() < 2 -> "Yesterday"
            elapsed.toDays() < 7 -> "${elapsed.toDays()}d ago"
            else -> {
                val formatter = DateTimeFormatter.ofPattern("MMM d")
                snoozeEndTime.format(formatter)
            }
        }
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
                groupKey = notification.groupKey,
                messages = notification.messages  // Preserve messages!
            )
        }

        /**
         * Create a snooze record from shared content.
         */
        fun fromSharedContent(
            content: SharedContent,
            endTime: LocalDateTime,
            context: Context
        ): SnoozeRecord {
            return SnoozeRecord(
                threadId = content.extractUrl() ?: content.text ?: UUID.randomUUID().toString(),
                notificationKey = null,
                packageName = content.sourcePackage ?: "unknown",
                appName = content.sourcePackage?.let {
                    AppNameResolver.getAppName(context, it)
                } ?: "Shared Content",
                title = content.getDisplayTitle(),
                text = content.text,
                snoozeEndTime = endTime,
                source = SnoozeSource.SHARE_SHEET,
                contentType = content.type.name  // Persist content type
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
