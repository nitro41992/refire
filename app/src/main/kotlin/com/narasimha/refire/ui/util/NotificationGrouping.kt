package com.narasimha.refire.ui.util

import com.narasimha.refire.data.model.MessageData
import com.narasimha.refire.data.model.NotificationInfo
import com.narasimha.refire.data.model.SnoozeRecord

/**
 * Create synthetic messages from notification content when MessagingStyle messages don't exist.
 * This handles apps like Blip that group notifications without using MessagingStyle.
 */
private fun createSyntheticMessages(notifications: List<NotificationInfo>): List<MessageData> {
    return notifications
        .sortedByDescending { it.postTime }
        .mapNotNull { notif ->
            val content = notif.title ?: notif.text
            if (content.isNullOrBlank()) null
            else MessageData(
                sender = "",
                text = content,
                timestamp = notif.postTime
            )
        }
}

/**
 * Merge messages from multiple notifications, creating synthetic messages if needed.
 */
fun mergeNotificationMessages(notifications: List<NotificationInfo>): List<MessageData> {
    // Collect all MessagingStyle messages
    val allMessages = notifications
        .flatMap { it.messages }
        .distinctBy { it.timestamp }
        .sortedByDescending { it.timestamp }

    // If no MessagingStyle messages exist, create synthetic messages from notification content
    return if (allMessages.isEmpty()) {
        createSyntheticMessages(notifications)
    } else {
        allMessages
    }
}

/**
 * Aggregate notifications by thread identifier.
 * Returns list where each NotificationInfo represents a group with merged messages.
 *
 * Usage:
 * ```
 * val groupedNotifications = notifications.groupNotificationsByThread()
 * ```
 */
fun List<NotificationInfo>.groupNotificationsByThread(): List<NotificationInfo> {
    return this
        .groupBy { it.getThreadIdentifier() }
        .map { (_, notificationsInGroup) ->
            if (notificationsInGroup.size == 1) {
                notificationsInGroup.first()
            } else {
                val mostRecent = notificationsInGroup.maxByOrNull { it.postTime }!!
                val mergedMessages = mergeNotificationMessages(notificationsInGroup)

                // For grouped notifications without MessagingStyle, use a better title
                val title = if (mergedMessages.isNotEmpty() && mostRecent.messages.isEmpty()) {
                    // Synthetic messages created - use count-based title
                    "${mergedMessages.size} items"
                } else {
                    // MessagingStyle or single notification - keep original title
                    mostRecent.title
                }

                mostRecent.copy(
                    title = title,
                    messages = mergedMessages
                )
            }
        }
        .sortedByDescending { it.postTime }
}

/**
 * Merge messages from multiple snooze records, creating synthetic messages if needed.
 */
private fun mergeSnoozeMessages(records: List<SnoozeRecord>): List<MessageData> {
    // Collect all MessagingStyle messages
    val allMessages = records
        .flatMap { it.messages }
        .distinctBy { it.timestamp }
        .sortedByDescending { it.timestamp }

    // If no MessagingStyle messages exist, create synthetic messages from snooze content
    return if (allMessages.isEmpty()) {
        records
            .sortedByDescending { it.createdAt }
            .mapNotNull { record ->
                val content = record.title
                if (content.isBlank()) null
                else MessageData(
                    sender = "",
                    text = content,
                    timestamp = record.createdAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                )
            }
    } else {
        allMessages
    }
}

/**
 * Aggregate snooze records by thread identifier.
 * Returns list where each SnoozeRecord represents a group with merged messages.
 *
 * Usage:
 * ```
 * val groupedSnoozes = snoozeRecords.groupSnoozesByThread()
 * ```
 */
fun List<SnoozeRecord>.groupSnoozesByThread(): List<SnoozeRecord> {
    return this
        .groupBy { it.threadId }
        .map { (_, recordsInGroup) ->
            if (recordsInGroup.size == 1) {
                recordsInGroup.first()
            } else {
                val mostRecent = recordsInGroup.maxByOrNull { it.createdAt }!!
                val latestEndTime = recordsInGroup.maxByOrNull { it.snoozeEndTime }!!.snoozeEndTime
                val mergedMessages = mergeSnoozeMessages(recordsInGroup)

                // For grouped snoozes without MessagingStyle, use a better title
                val title = if (mergedMessages.isNotEmpty() && mostRecent.messages.isEmpty()) {
                    // Synthetic messages created - use count-based title
                    "${mergedMessages.size} items"
                } else {
                    // MessagingStyle or single snooze - keep original title
                    mostRecent.title
                }

                mostRecent.copy(
                    title = title,
                    messages = mergedMessages,
                    snoozeEndTime = latestEndTime
                )
            }
        }
        .sortedBy { it.snoozeEndTime }
}
