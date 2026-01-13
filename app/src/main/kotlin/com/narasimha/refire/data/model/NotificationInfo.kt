package com.narasimha.refire.data.model

import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.narasimha.refire.core.util.AppNameResolver

/**
 * Represents extracted metadata from a StatusBarNotification.
 * Structured for easy Room Entity conversion in Phase 2.
 */
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
    val messages: List<MessageData> = emptyList()  // Extracted messages from MessagingStyle
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
                messages = messages
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
     * Priority: groupKey > shortcutId > packageName (app-level fallback)
     *
     * groupKey is prioritized because it matches Android's native notification grouping behavior.
     * shortcutId is used for conversation shortcuts/bubbles, which may differ from grouping.
     */
    fun getThreadIdentifier(): String {
        return groupKey ?: shortcutId ?: packageName
    }
}
