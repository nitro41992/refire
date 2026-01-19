package com.narasimha.refire.data.model

import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.Stable
import com.narasimha.refire.core.util.AppNameResolver

/**
 * Represents extracted metadata from a StatusBarNotification.
 * Structured for easy Room Entity conversion in Phase 2.
 *
 * Note: Uses @Stable instead of @Immutable because PendingIntent and Bundle are mutable types.
 * Compose will use reference equality for recomposition decisions.
 */
@Stable
data class NotificationInfo(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?,
    val conversationTitle: String?,
    val shortcutId: String?,
    val groupKey: String?,
    val postTime: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val messages: List<MessageData> = emptyList(),  // Extracted messages from MessagingStyle
    val contentIntent: PendingIntent? = null,  // Original intent for jump-back navigation
    val extras: Bundle? = null,  // Raw notification extras for URI extraction
    val category: String? = null,  // Notification category (msg, social, etc.)
    val channelId: String? = null  // Notification channel for type-level filtering
) {
    companion object {
        // Generic/placeholder titles that should be replaced with text content
        private val GENERIC_TITLES = setOf(
            "notification",
            "new notification",
            "new message",
            "message"
        )

        fun fromStatusBarNotification(sbn: StatusBarNotification, context: Context): NotificationInfo {
            val extras = sbn.notification.extras

            // Extract messages from MessagingStyle
            val messages = extractMessages(extras)

            val rawTitle = extras.getCharSequence("android.title")?.toString()
            val rawText = extras.getCharSequence("android.text")?.toString()

            // If title is generic (like "Notification"), use text as title instead
            val (title, text) = if (rawTitle != null &&
                GENERIC_TITLES.contains(rawTitle.lowercase().trim()) &&
                !rawText.isNullOrBlank()
            ) {
                // Swap: use text as title, bigText or subText as the new text
                val bigText = extras.getCharSequence("android.bigText")?.toString()
                Pair(rawText, bigText)
            } else {
                Pair(rawTitle, rawText)
            }

            return NotificationInfo(
                key = sbn.key,
                packageName = sbn.packageName,
                appName = AppNameResolver.getAppName(context, sbn.packageName),
                title = title,
                text = text,
                bigText = extras.getCharSequence("android.bigText")?.toString(),
                subText = extras.getCharSequence("android.subText")?.toString(),
                conversationTitle = extras.getCharSequence("android.conversationTitle")?.toString(),
                shortcutId = sbn.notification.shortcutId,
                groupKey = sbn.groupKey,
                postTime = sbn.postTime,
                messages = messages,
                contentIntent = sbn.notification.contentIntent,  // Capture for jump-back
                extras = extras,  // Store for URI extraction
                category = sbn.notification.category,  // For conversation filtering
                channelId = sbn.notification.channelId  // For type-level filtering
            )
        }

        private fun extractMessages(extras: Bundle): List<MessageData> {
            val messages = mutableListOf<MessageData>()

            // Try MessagingStyle (used by Messages, WhatsApp, Slack, etc.)
            val messageArray = extras.getParcelableArray("android.messages")
            if (messageArray != null && messageArray.isNotEmpty()) {
                for (msg in messageArray) {
                    val bundle = msg as? Bundle ?: continue

                    val sender = bundle.getCharSequence("sender")?.toString() ?: continue
                    val text = bundle.getCharSequence("text")?.toString() ?: continue
                    val time = bundle.getLong("time", 0)

                    messages.add(MessageData(
                        sender = sender,
                        text = text,
                        timestamp = time
                    ))
                }
                return messages
            }

            // Fallback: Try InboxStyle (used by email apps)
            val lines = extras.getCharSequenceArray("android.textLines")
            if (lines != null && lines.isNotEmpty()) {
                lines.forEach { line ->
                    messages.add(MessageData(
                        sender = "",  // InboxStyle doesn't have sender info
                        text = line.toString(),
                        timestamp = 0
                    ))
                }
                return messages
            }

            // No grouped messages found - return empty list
            return emptyList()
        }
    }

    /**
     * Returns the best available text content for display/summarization.
     * Prioritizes extracted messages over summary text.
     */
    fun getBestTextContent(): String {
        // If we have extracted messages, show them as a summary
        if (messages.isNotEmpty()) {
            return messages.joinToString("\n") { it.text }
        }

        // Fallback to original summary text
        return bigText ?: text ?: ""
    }

    /**
     * Returns the thread identifier for conversation-level matching.
     * Priority: shortcutId > groupKey > packageName (app-level fallback)
     *
     * shortcutId is prioritized because it provides conversation-level precision (used for
     * bubbles/shortcuts). Apps like Discord use a shared groupKey for all conversations but
     * unique shortcutIds per channel, so shortcutId gives better conversation-level targeting.
     */
    fun getThreadIdentifier(): String {
        return shortcutId ?: groupKey ?: packageName
    }

    /**
     * Returns the identifier for ignore-list matching.
     * Different from getThreadIdentifier() because non-conversation notifications
     * (like "Adaptive Charging") need channel-level precision, not app-level.
     *
     * Priority:
     * - Conversations (have shortcutId): use shortcutId for conversation-level precision
     * - Non-conversations: use channel:$channelId:$packageName for notification-type-level precision
     * - Fallback: packageName (app-level)
     */
    fun getIgnoreIdentifier(): String {
        // Conversations: use shortcutId for conversation-level precision
        shortcutId?.let { return it }
        // Non-conversations: use channel for notification-type-level precision
        channelId?.let { return "channel:$it:$packageName" }
        // Fallback: app-level
        return packageName
    }

    /**
     * Check if this is a media notification (has MediaSession).
     * Media notifications' contentIntent often doesn't open the app, so we skip it.
     */
    fun isMediaNotification(): Boolean {
        return extras?.get("android.mediaSession") != null
    }

    /**
     * Check if this notification is from a conversation/messaging app.
     * Primary: Uses Android's official category system.
     * Fallback: For items without category (pre-migration), use shortcutId as heuristic.
     */
    fun isConversation(): Boolean {
        // Primary: Android's official category
        if (category == android.app.Notification.CATEGORY_MESSAGE ||
            category == android.app.Notification.CATEGORY_SOCIAL) {
            return true
        }
        // Fallback for pre-existing items: shortcutId indicates conversation-level targeting
        // (Discord channels, WhatsApp chats, etc. all set shortcutId)
        if (category == null && shortcutId != null) {
            return true
        }
        return false
    }

    /**
     * Get a human-readable name for the notification type.
     * Only meaningful for conversations - returns the sender/chat name.
     * For non-conversations, the dialog uses static text instead.
     */
    fun getNotificationTypeName(): String {
        return title ?: appName
    }

    /**
     * Convert this notification to a SnoozeRecord for history tracking.
     * Used when a notification is dismissed (not scheduled).
     */
    fun toDismissedRecord(): SnoozeRecord {
        return SnoozeRecord(
            threadId = getThreadIdentifier(),
            notificationKey = key,
            packageName = packageName,
            appName = appName,
            title = title
                ?: conversationTitle
                ?: messages.firstOrNull()?.sender?.takeIf { it.isNotBlank() }
                ?: text?.take(50)
                ?: "Unknown",
            text = text,
            snoozeEndTime = java.time.LocalDateTime.now(),
            source = SnoozeSource.NOTIFICATION,
            shortcutId = shortcutId,
            groupKey = groupKey,
            messages = messages,
            status = SnoozeStatus.DISMISSED,
            category = category,
            channelId = channelId
        )
    }

    /**
     * Format the time since this notification was posted.
     * Returns relative time like "2m ago", "1h ago", "Yesterday".
     */
    fun formattedTimeSincePosted(): String {
        val now = System.currentTimeMillis()
        val elapsed = now - postTime
        val minutes = elapsed / 60_000
        val hours = elapsed / 3_600_000
        val days = elapsed / 86_400_000

        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 2 -> "Yesterday"
            days < 7 -> "${days}d ago"
            else -> {
                val formatter = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
                formatter.format(java.util.Date(postTime))
            }
        }
    }
}
