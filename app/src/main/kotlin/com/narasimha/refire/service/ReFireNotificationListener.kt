package com.narasimha.refire.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.narasimha.refire.data.model.NotificationInfo
import com.narasimha.refire.data.model.SnoozeRecord
import com.narasimha.refire.data.model.SnoozeSource
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

        val info = NotificationInfo.fromStatusBarNotification(sbn)
        val threadId = info.getThreadIdentifier()

        Log.d(TAG, "Notification posted: ${info.packageName} | Thread: $threadId | Title: ${info.title}")

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

        if (shouldIgnoreNotification(sbn)) return

        val info = NotificationInfo.fromStatusBarNotification(sbn)

        // Update active notifications list
        refreshActiveNotifications()

        // Add to recents only if dismissed by user action
        if (reason == REASON_CANCEL || reason == REASON_CLICK) {
            addToRecentsBuffer(info)

            Log.d(TAG, "Notification removed (user action): ${info.packageName} | ${info.title}")

            serviceScope.launch {
                _notificationEvents.emit(NotificationEvent.NotificationDismissed(info))
            }
        }
    }

    /**
     * Refresh the active notifications list from system.
     */
    private fun refreshActiveNotifications() {
        try {
            val notifications = activeNotifications
                .mapNotNull { sbn ->
                    if (shouldIgnoreNotification(sbn)) null
                    else NotificationInfo.fromStatusBarNotification(sbn)
                }
                .sortedByDescending { it.postTime }

            _activeNotifications.value = notifications
            Log.d(TAG, "Refreshed active notifications: ${notifications.size} items")
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing active notifications", e)
        }
    }

    private fun addSnoozeRecord(record: SnoozeRecord) {
        val current = _snoozeRecords.value.toMutableList()

        // Remove any existing snooze for the same thread
        current.removeAll { it.threadId == record.threadId }

        // Add new record
        current.add(0, record)

        _snoozeRecords.value = current

        // Clean up expired snoozes
        cleanupExpiredSnoozes()
    }

    private fun removeSnoozeRecord(snoozeId: String) {
        val current = _snoozeRecords.value.toMutableList()
        current.removeAll { it.id == snoozeId }
        _snoozeRecords.value = current
    }

    private fun updateSnoozeEndTime(snoozeId: String, newEndTime: LocalDateTime) {
        val current = _snoozeRecords.value.toMutableList()
        val index = current.indexOfFirst { it.id == snoozeId }

        if (index >= 0) {
            current[index] = current[index].copy(snoozeEndTime = newEndTime)
            _snoozeRecords.value = current
        }
    }

    private fun cleanupExpiredSnoozes() {
        val current = _snoozeRecords.value
        val active = current.filter { !it.isExpired() }

        if (active.size != current.size) {
            _snoozeRecords.value = active
            Log.d(TAG, "Cleaned up ${current.size - active.size} expired snoozes")
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

        // Remove duplicate (same thread)
        current.removeAll { it.getThreadIdentifier() == info.getThreadIdentifier() }

        // Add to front
        current.add(0, info)

        // Trim to max size
        if (current.size > RECENTS_BUFFER_SIZE) {
            current.removeAt(current.lastIndex)
        }

        _recentsBuffer.value = current
    }

    private fun shouldIgnoreNotification(sbn: StatusBarNotification): Boolean {
        // Ignore our own notifications
        if (sbn.packageName == packageName) return true

        // Ignore system UI
        if (sbn.packageName == "android" || sbn.packageName == "com.android.systemui") return true

        // Ignore ongoing/persistent notifications
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return true
        if (notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return true

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
