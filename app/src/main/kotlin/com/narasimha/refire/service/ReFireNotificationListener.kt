package com.narasimha.refire.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.narasimha.refire.data.model.MessageData
import com.narasimha.refire.data.model.NotificationInfo
import com.narasimha.refire.data.model.SnoozeRecord
import com.narasimha.refire.data.model.SnoozeSource
import com.narasimha.refire.ui.util.mergeNotificationMessages
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Core notification interception service for Re-Fire.
 *
 * Responsibilities:
 * - Intercept all incoming notifications
 * - Track active notifications for in-app display
 * - Cancel (suppress) notifications for snoozed threads
 * - Manage snooze records with cancel/extend capability
 */
class ReFireNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var repository: com.narasimha.refire.data.repository.SnoozeRepository
    private lateinit var alarmHelper: com.narasimha.refire.core.util.AlarmManagerHelper

    private val _activeNotifications = MutableStateFlow<List<NotificationInfo>>(emptyList())
    private val _snoozeRecords = MutableStateFlow<List<SnoozeRecord>>(emptyList())
    private val _recentsBuffer = MutableStateFlow<List<NotificationInfo>>(emptyList())

    private val _notificationEvents = MutableSharedFlow<NotificationEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )

    companion object {
        private const val TAG = "ReFireListener"
        private const val RECENTS_BUFFER_SIZE = 10

        @Volatile
        private var instance: ReFireNotificationListener? = null

        fun getInstance(): ReFireNotificationListener? = instance

        val activeNotifications: StateFlow<List<NotificationInfo>>
            get() = instance?._activeNotifications?.asStateFlow()
                ?: MutableStateFlow(emptyList())

        val snoozeRecords: StateFlow<List<SnoozeRecord>>
            get() = instance?._snoozeRecords?.asStateFlow()
                ?: MutableStateFlow(emptyList())

        val recentsBuffer: StateFlow<List<NotificationInfo>>
            get() = instance?._recentsBuffer?.asStateFlow()
                ?: MutableStateFlow(emptyList())

        val notificationEvents: SharedFlow<NotificationEvent>
            get() = instance?._notificationEvents?.asSharedFlow()
                ?: MutableSharedFlow()

        fun isConnected(): Boolean = instance != null

        /**
         * Snooze a notification from the in-app list.
         * Dismisses it from the system tray and tracks the snooze.
         */
        fun snoozeNotification(info: NotificationInfo, endTime: LocalDateTime) {
            val inst = instance ?: return

            val record = SnoozeRecord.fromNotification(info, endTime)
            inst.addSnoozeRecord(record)

            // Cancel the notification from system tray
            inst.cancelNotificationSilently(info.key)

            // Remove from recents buffer (for notifications snoozed from Recently Dismissed)
            inst.removeFromRecentsBuffer(info.getThreadIdentifier())

            // Refresh active notifications
            inst.refreshActiveNotifications()

            Log.i(TAG, "Snoozed notification: ${info.title} until $endTime")
        }

        /**
         * Add a snooze record from share sheet.
         */
        fun addSnoozeFromShareSheet(record: SnoozeRecord) {
            instance?.addSnoozeRecord(record)
            Log.i(TAG, "Added share sheet snooze: ${record.title} until ${record.snoozeEndTime}")
        }

        /**
         * Cancel an active snooze.
         */
        fun cancelSnooze(snoozeId: String) {
            instance?.removeSnoozeRecord(snoozeId)
            Log.i(TAG, "Canceled snooze: $snoozeId")
        }

        /**
         * Extend an active snooze with a new end time.
         */
        fun extendSnooze(snoozeId: String, newEndTime: LocalDateTime) {
            instance?.updateSnoozeEndTime(snoozeId, newEndTime)
            Log.i(TAG, "Extended snooze $snoozeId to $newEndTime")
        }

        /**
         * Check if a thread is currently snoozed.
         */
        fun isThreadSnoozed(threadId: String): Boolean {
            return instance?._snoozeRecords?.value?.any {
                it.threadId == threadId && !it.isExpired()
            } == true
        }

        /**
         * Force refresh active notifications.
         */
        fun refreshNotifications() {
            instance?.refreshActiveNotifications()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize database repository and alarm helper
        val database = com.narasimha.refire.data.database.ReFireDatabase.getInstance(applicationContext)
        repository = com.narasimha.refire.data.repository.SnoozeRepository(database.snoozeDao())
        alarmHelper = com.narasimha.refire.core.util.AlarmManagerHelper(applicationContext)

        Log.i(TAG, "NotificationListenerService created")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
        Log.i(TAG, "NotificationListenerService destroyed")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Listener connected - notification access active")

        // Populate initial active notifications
        refreshActiveNotifications()

        // Load persisted snoozes from database
        serviceScope.launch {
            repository.activeSnoozes.collect { records ->
                _snoozeRecords.value = records
            }
        }

        serviceScope.launch {
            _notificationEvents.emit(NotificationEvent.ServiceConnected)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Listener disconnected - notification access may have been revoked")

        // Clear active notifications
        _activeNotifications.value = emptyList()

        serviceScope.launch {
            _notificationEvents.emit(NotificationEvent.ServiceDisconnected)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        if (shouldIgnoreNotification(sbn)) return

        val info = NotificationInfo.fromStatusBarNotification(sbn, applicationContext)
        val threadId = info.getThreadIdentifier()
        val isGroupSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0

        Log.d(TAG, "Notification posted: ${info.packageName} | Thread: $threadId | Title: ${info.title} | Text: ${info.text} | Messages: ${info.messages.size} | GroupSummary: $isGroupSummary | GroupKey: ${info.groupKey} | ShortcutId: ${info.shortcutId}")

        // Check if this thread is snoozed
        val snoozedRecord = _snoozeRecords.value.find {
            it.threadId == threadId && !it.isExpired()
        }

        if (snoozedRecord != null) {
            Log.i(TAG, "Canceling notification for snoozed thread: $threadId")
            cancelNotificationSilently(sbn.key)

            serviceScope.launch {
                _notificationEvents.emit(NotificationEvent.NotificationSuppressed(info))
            }
            return
        }

        // Update active notifications list
        refreshActiveNotifications()

        serviceScope.launch {
            _notificationEvents.emit(NotificationEvent.NotificationPosted(info))
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?,
        rankingMap: RankingMap?,
        reason: Int
    ) {
        sbn ?: return

        // Log all removals before filtering for debugging
        val reasonName = when (reason) {
            REASON_CANCEL -> "CANCEL"
            REASON_CANCEL_ALL -> "CANCEL_ALL"
            REASON_CLICK -> "CLICK"
            REASON_ERROR -> "ERROR"
            REASON_GROUP_SUMMARY_CANCELED -> "GROUP_SUMMARY_CANCELED"
            REASON_GROUP_OPTIMIZATION -> "GROUP_OPTIMIZATION"
            REASON_LISTENER_CANCEL -> "LISTENER_CANCEL"
            REASON_LISTENER_CANCEL_ALL -> "LISTENER_CANCEL_ALL"
            REASON_APP_CANCEL -> "APP_CANCEL"
            REASON_APP_CANCEL_ALL -> "APP_CANCEL_ALL"
            REASON_PACKAGE_CHANGED -> "PACKAGE_CHANGED"
            REASON_PACKAGE_SUSPENDED -> "PACKAGE_SUSPENDED"
            REASON_PROFILE_TURNED_OFF -> "PROFILE_TURNED_OFF"
            REASON_UNAUTOBUNDLED -> "UNAUTOBUNDLED"
            REASON_CHANNEL_BANNED -> "CHANNEL_BANNED"
            REASON_SNOOZED -> "SNOOZED"
            REASON_TIMEOUT -> "TIMEOUT"
            else -> "UNKNOWN($reason)"
        }
        val title = sbn.notification.extras.getCharSequence("android.title")?.toString() ?: "no title"
        val isGroupSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        Log.d(TAG, "onNotificationRemoved BEFORE filter: ${sbn.packageName} | $title | Reason: $reasonName | GroupSummary: $isGroupSummary")

        if (shouldIgnoreNotification(sbn)) {
            Log.d(TAG, "  -> FILTERED OUT by shouldIgnoreNotification")
            return
        }

        val info = NotificationInfo.fromStatusBarNotification(sbn, applicationContext)
        Log.d(TAG, "  -> PASSED filter, processing removal | isGroupSummary: $isGroupSummary | messages: ${info.messages.size}")

        // BEFORE refreshing active list, capture all notifications from the same group
        // This ensures we preserve all messages when a grouped notification is dismissed
        val threadId = info.getThreadIdentifier()
        val allNotificationsInGroup = _activeNotifications.value
            .filter { it.getThreadIdentifier() == threadId && it.key != info.key }  // Same thread, excluding dismissed
            .plus(info)  // Add the dismissed notification back (avoiding duplicate)

        // Use centralized merging logic to create synthetic messages if needed
        val mergedMessages = mergeNotificationMessages(allNotificationsInGroup)

        // For grouped notifications without MessagingStyle, use a better title
        // Only use count-based title when there's more than 1 item
        val groupedTitle = if (mergedMessages.size > 1 && info.messages.isEmpty()) {
            // Synthetic messages created - use count-based title
            "${mergedMessages.size} Items"
        } else {
            // MessagingStyle or single notification - keep original title
            info.title
        }

        val infoWithAllMessages = info.copy(
            title = groupedTitle,
            messages = mergedMessages
        )

        Log.d(TAG, "Captured ${mergedMessages.size} total messages from group (was ${info.messages.size})")

        // Update active notifications list
        refreshActiveNotifications()

        // Add to recents if dismissed by user action (including grouped dismissals)
        val isUserAction = reason == REASON_CANCEL ||
                          reason == REASON_CANCEL_ALL ||
                          reason == REASON_CLICK ||
                          reason == REASON_GROUP_SUMMARY_CANCELED

        if (isUserAction) {
            // Add to recents - all notifications dismissed by user action
            // Note: We add both child notifications and summaries because:
            // - Some apps (SMS) dismiss summaries automatically (APP_CANCEL) when children are dismissed
            // - Some apps (Blip) dismiss children automatically when summary is dismissed
            // We let addToRecentsBuffer handle deduplication
            addToRecentsBuffer(infoWithAllMessages)
            Log.d(TAG, "Added to recents buffer: ${infoWithAllMessages.packageName} | ${infoWithAllMessages.title} | isGroupSummary: $isGroupSummary | messages: ${infoWithAllMessages.messages.size}")

            serviceScope.launch {
                _notificationEvents.emit(NotificationEvent.NotificationDismissed(infoWithAllMessages))
            }
        }
    }

    /**
     * Refresh the active notifications list from system.
     * Filters out group summary notifications when child notifications exist.
     */
    private fun refreshActiveNotifications() {
        try {
            val rawNotifications = activeNotifications
                .filter { !shouldIgnoreNotification(it) }
                .toList()

            // Identify groups that have child notifications
            val groupsWithChildren = rawNotifications
                .filter { sbn ->
                    val isGroupSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
                    !isGroupSummary && sbn.groupKey != null
                }
                .map { it.groupKey }
                .toSet()

            // Filter out group summaries when children exist
            val notifications = rawNotifications
                .filter { sbn ->
                    val isGroupSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
                    // Keep if: not a summary, OR if summary but no children in this group
                    !isGroupSummary || sbn.groupKey !in groupsWithChildren
                }
                .map { sbn ->
                    NotificationInfo.fromStatusBarNotification(sbn, applicationContext)
                }
                .sortedByDescending { it.postTime }

            _activeNotifications.value = notifications
            Log.d(TAG, "Refreshed active notifications: ${notifications.size} items (filtered ${rawNotifications.size - notifications.size} group summaries)")
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing active notifications", e)
        }
    }

    private fun addSnoozeRecord(record: SnoozeRecord) {
        serviceScope.launch {
            // Persist to database (auto-deduplicates by thread)
            repository.insertSnooze(record)

            // Schedule alarm for expiration
            alarmHelper.scheduleSnoozeAlarm(record.id, record.toEpochMillis())

            Log.i(TAG, "Persisted snooze ${record.id} and scheduled alarm for ${record.snoozeEndTime}")
        }
    }

    private fun removeSnoozeRecord(snoozeId: String) {
        serviceScope.launch {
            // Cancel scheduled alarm
            alarmHelper.cancelSnoozeAlarm(snoozeId)

            // Remove from database
            repository.deleteSnooze(snoozeId)

            Log.i(TAG, "Removed snooze $snoozeId and canceled alarm")
        }
    }

    private fun updateSnoozeEndTime(snoozeId: String, newEndTime: LocalDateTime) {
        serviceScope.launch {
            // Update database
            repository.updateSnoozeEndTime(snoozeId, newEndTime)

            // Reschedule alarm
            val triggerTimeMillis = newEndTime
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            alarmHelper.cancelSnoozeAlarm(snoozeId)
            alarmHelper.scheduleSnoozeAlarm(snoozeId, triggerTimeMillis)

            Log.i(TAG, "Extended snooze $snoozeId to $newEndTime and rescheduled alarm")
        }
    }

    private fun cancelNotificationSilently(key: String) {
        try {
            cancelNotification(key)
            Log.d(TAG, "Successfully canceled notification: $key")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to cancel notification (permission issue): $key", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error canceling notification: $key", e)
        }
    }

    private fun addToRecentsBuffer(info: NotificationInfo) {
        val current = _recentsBuffer.value.toMutableList()

        // Check if we already have a notification from the same thread
        val threadId = info.getThreadIdentifier()
        val existingIndex = current.indexOfFirst {
            it.getThreadIdentifier() == threadId
        }

        if (existingIndex != -1) {
            // Merge into existing notification
            val existing = current[existingIndex]

            // Use centralized merging logic to handle both MessagingStyle and non-MessagingStyle
            val finalMessages = mergeNotificationMessages(listOf(existing, info))

            // For grouped notifications without MessagingStyle, use a better title
            // Only use count-based title when there's more than 1 item
            val mergedTitle = if (finalMessages.size > 1 && existing.messages.isEmpty() && info.messages.isEmpty()) {
                // Synthetic messages created - use count-based title
                "${finalMessages.size} Items"
            } else {
                // MessagingStyle or single notification - keep most recent title
                if (info.postTime > existing.postTime) info.title else existing.title
            }

            val merged = existing.copy(
                key = if (info.postTime > existing.postTime) info.key else existing.key,
                title = mergedTitle,
                postTime = maxOf(info.postTime, existing.postTime),
                messages = finalMessages,
                timestamp = System.currentTimeMillis()
            )

            current.removeAt(existingIndex)
            current.add(0, merged)
            Log.d(TAG, "Merged into existing thread: $threadId (${finalMessages.size} messages)")
        } else {
            // New thread - add to front
            current.add(0, info)
            Log.d(TAG, "Added new thread to recents: $threadId")
        }

        // Trim to max size
        if (current.size > RECENTS_BUFFER_SIZE) {
            current.removeAt(current.lastIndex)
        }

        _recentsBuffer.value = current
        Log.d(TAG, "Recents buffer now has ${current.size} items")
    }

    private fun removeFromRecentsBuffer(threadId: String) {
        val current = _recentsBuffer.value.toMutableList()
        val removed = current.removeAll { it.getThreadIdentifier() == threadId }

        if (removed) {
            _recentsBuffer.value = current
            Log.d(TAG, "Removed thread $threadId from recents buffer")
        }
    }

    private fun isFromGroupedNotification(sbn: StatusBarNotification): Boolean {
        return sbn.groupKey != null &&
               sbn.notification.group != null
    }

    private fun shouldIgnoreNotification(sbn: StatusBarNotification): Boolean {
        // 1. Ignore our own notifications
        if (sbn.packageName == packageName) return true

        // 2. Block common system packages
        val systemPackages = setOf(
            "android",
            "com.android.systemui",
            "com.google.android.gms",
            "com.google.android.googlequicksearchbox",
            "com.google.android.apps.genie.geniewidget",
            "com.google.android.as",
            "com.google.android.apps.mediashell",
            "com.google.android.apps.translate",
            "com.android.vending",  // Play Store
            "com.google.android.projection.gearhead"  // Android Auto
        )
        if (systemPackages.contains(sbn.packageName)) {
            Log.d(TAG, "Filtered system package: ${sbn.packageName}")
            return true
        }

        // 3. Block OEM system packages
        if (sbn.packageName.startsWith("com.samsung.android.") ||
            sbn.packageName.startsWith("com.miui.") ||
            sbn.packageName.startsWith("com.huawei.") ||
            sbn.packageName.startsWith("com.lge.")) {
            Log.d(TAG, "Filtered OEM package: ${sbn.packageName}")
            return true
        }

        // 4. Check notification properties
        val notification = sbn.notification

        // Filter ongoing/persistent notifications that can't be dismissed
        // These include task reminders, timers, music players - they have native snooze functionality
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return true
        if (notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return true
        if (notification.flags and Notification.FLAG_INSISTENT != 0) return true

        // System categories (only truly system-critical ones)
        val systemCategories = setOf(
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_SYSTEM
        )
        if (notification.category in systemCategories) {
            Log.d(TAG, "Filtered system category: ${notification.category}")
            return true
        }

        // 5. Check for empty content
        // Group summaries can have empty title/text (children have the content), so skip check for them
        val isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        val extras = notification.extras
        val title = extras.getCharSequence("android.title")?.toString()

        if (!isGroupSummary) {
            val text = extras.getCharSequence("android.text")?.toString()
            val bigText = extras.getCharSequence("android.bigText")?.toString()
            val hasMessages = extras.getParcelableArray("android.messages")?.isNotEmpty() == true ||
                             extras.getCharSequenceArray("android.textLines")?.isNotEmpty() == true

            if (title.isNullOrBlank() && text.isNullOrBlank() && bigText.isNullOrBlank() && !hasMessages) {
                Log.d(TAG, "Filtered empty notification from: ${sbn.packageName}")
                return true
            }
        }

        // 6. Check for auth/security keywords in title
        val authKeywords = setOf("verify", "sign in", "2fa", "authenticate", "confirm identity", "security")
        val lowerTitle = title?.lowercase() ?: ""
        if (authKeywords.any { lowerTitle.contains(it) }) {
            Log.d(TAG, "Filtered auth notification: $title")
            return true
        }

        return false
    }

    sealed class NotificationEvent {
        data object ServiceConnected : NotificationEvent()
        data object ServiceDisconnected : NotificationEvent()
        data class NotificationPosted(val info: NotificationInfo) : NotificationEvent()
        data class NotificationSuppressed(val info: NotificationInfo) : NotificationEvent()
        data class NotificationDismissed(val info: NotificationInfo) : NotificationEvent()
    }
}
