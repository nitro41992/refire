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
    // Deduplicate by content (sender|text) not timestamp - same message can have different timestamps
    val allMessages = notifications
        .flatMap { it.messages }
        .distinctBy { "${it.sender.trim()}|${it.text.trim()}" }
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
                val single = notificationsInGroup.first()
                // If title is null/blank but text exists, use text as title
                if (single.title.isNullOrBlank() && !single.text.isNullOrBlank()) {
                    single.copy(title = single.text, text = single.bigText)
                } else {
                    single
                }
            } else {
                val mostRecent = notificationsInGroup.maxByOrNull { it.postTime }!!
                val mergedMessages = mergeNotificationMessages(notificationsInGroup)

                // Find the best available title from the group (prefer non-null, most recent)
                val bestTitle = notificationsInGroup
                    .filter { !it.title.isNullOrBlank() }
                    .maxByOrNull { it.postTime }
                    ?.title
                    ?: mostRecent.title

                // For grouped notifications without MessagingStyle, use a better title
                // Only use count-based title when there's more than 1 item
                val title = if (mergedMessages.size > 1 && mostRecent.messages.isEmpty()) {
                    // Synthetic messages created - use count-based title
                    "${mergedMessages.size} Items"
                } else {
                    // MessagingStyle or single notification - use best available title
                    bestTitle
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
    // Deduplicate by content (sender|text) not timestamp - same message can have different timestamps
    val allMessages = records
        .flatMap { it.messages }
        .distinctBy { "${it.sender.trim()}|${it.text.trim()}" }
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
                // Only use count-based title when there's more than 1 item
                val title = if (mergedMessages.size > 1 && mostRecent.messages.isEmpty()) {
                    // Synthetic messages created - use count-based title
                    "${mergedMessages.size} Items"
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

/**
 * Filter out messages from Active notifications that already exist in EXPIRED history.
 * This creates a "new lifecycle" where only messages that arrived AFTER a snooze expired are shown.
 *
 * Usage:
 * ```
 * val filteredActive = groupedNotifications.filterOutExpiredMessages(expiredHistory)
 * ```
 */
fun List<NotificationInfo>.filterOutExpiredMessages(
    expiredHistory: List<SnoozeRecord>
): List<NotificationInfo> {
    return this.map { notification ->
        val expiredForThread = expiredHistory.filter {
            it.threadId == notification.getThreadIdentifier()
        }

        // If no expired history for this thread, return as-is
        if (expiredForThread.isEmpty()) return@map notification

        // Get all messages from EXPIRED records for this thread
        val expiredMessageSet = expiredForThread
            .flatMap { it.messages }
            .map { "${it.sender.trim()}|${it.text.trim()}" }
            .toSet()

        // Filter out messages that exist in expired history
        val newMessages = notification.messages.filter {
            "${it.sender.trim()}|${it.text.trim()}" !in expiredMessageSet
        }

        notification.copy(messages = newMessages)
    }
}
