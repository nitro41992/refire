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
        fun fromStatusBarNotification(sbn: StatusBarNotification, context: Context): NotificationInfo {
            val extras = sbn.notification.extras

            // Extract messages from MessagingStyle
            val messages = extractMessages(extras)

            return NotificationInfo(
                key = sbn.key,
                packageName = sbn.packageName,
                appName = AppNameResolver.getAppName(context, sbn.packageName),
                title = extras.getCharSequence("android.title")?.toString(),
                text = extras.getCharSequence("android.text")?.toString(),
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
     * Priority: shortcutId > groupKey > packageName (app-level fallback)
     */
    fun getThreadIdentifier(): String {
        return shortcutId ?: groupKey ?: packageName
    }
}
