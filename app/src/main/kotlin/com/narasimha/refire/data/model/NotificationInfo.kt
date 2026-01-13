package com.narasimha.refire.data.model

import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.StatusBarNotification

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
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromStatusBarNotification(sbn: StatusBarNotification, context: Context): NotificationInfo {
            val extras = sbn.notification.extras

            return NotificationInfo(
                key = sbn.key,
                packageName = sbn.packageName,
                appName = getAppName(context, sbn.packageName),
                title = extras.getCharSequence("android.title")?.toString(),
                text = extras.getCharSequence("android.text")?.toString(),
                bigText = extras.getCharSequence("android.bigText")?.toString(),
                subText = extras.getCharSequence("android.subText")?.toString(),
                conversationTitle = extras.getCharSequence("android.conversationTitle")?.toString(),
                shortcutId = sbn.notification.shortcutId,
                groupKey = sbn.groupKey,
                postTime = sbn.postTime
            )
        }

        private fun getAppName(context: Context, packageName: String): String {
            return try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                // Fallback to formatted package name
                packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
            }
        }
    }

    /**
     * Returns the best available text content for display/summarization.
     */
    fun getBestTextContent(): String {
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
