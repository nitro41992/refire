package com.narasimha.refire.service

import android.app.Notification
import android.content.pm.ServiceInfo
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.ServiceCompat
import com.narasimha.refire.core.util.ContentIntentCache
import com.narasimha.refire.core.util.NotificationHelperManager
import com.narasimha.refire.data.preferences.RetentionPreferences
import com.narasimha.refire.data.model.MessageData
import com.narasimha.refire.data.model.NotificationInfo
import com.narasimha.refire.data.model.SnoozeRecord
import com.narasimha.refire.data.model.IgnoreScope
import com.narasimha.refire.data.model.SnoozeSource
import com.narasimha.refire.data.model.SnoozeStatus
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
class ReFireNotificationListener : NotificationListenerService(), NotificationHelperManager.ForegroundCallback {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var repository: com.narasimha.refire.data.repository.SnoozeRepository
    private lateinit var ignoredRepository: com.narasimha.refire.data.repository.IgnoredThreadRepository
    private lateinit var alarmHelper: com.narasimha.refire.core.util.AlarmManagerHelper
    private lateinit var helperManager: com.narasimha.refire.core.util.NotificationHelperManager

    private var isInForeground = false

    private val _activeNotifications = MutableStateFlow<List<NotificationInfo>>(emptyList())
    private val _snoozeRecords = MutableStateFlow<List<SnoozeRecord>>(emptyList())
    private val _historySnoozes = MutableStateFlow<List<SnoozeRecord>>(emptyList())
    private val _dismissedRecords = MutableStateFlow<List<SnoozeRecord>>(emptyList())
    private val _ignoredThreadIds = MutableStateFlow<Set<String>>(emptySet())

    private val _notificationEvents = MutableSharedFlow<NotificationEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )

    companion object {
        private const val TAG = "ReFireListener"

        @Volatile
        private var instance: ReFireNotificationListener? = null

        fun getInstance(): ReFireNotificationListener? = instance

        val activeNotifications: StateFlow<List<NotificationInfo>>
            get() = instance?._activeNotifications?.asStateFlow()
                ?: MutableStateFlow(emptyList())

        val snoozeRecords: StateFlow<List<SnoozeRecord>>
            get() = instance?._snoozeRecords?.asStateFlow()
                ?: MutableStateFlow(emptyList())

        val historySnoozes: StateFlow<List<SnoozeRecord>>
            get() = instance?._historySnoozes?.asStateFlow()
                ?: MutableStateFlow(emptyList())

        val dismissedRecords: StateFlow<List<SnoozeRecord>>
            get() = instance?._dismissedRecords?.asStateFlow()
                ?: MutableStateFlow(emptyList())

        val ignoredThreadIds: StateFlow<Set<String>>
            get() = instance?._ignoredThreadIds?.asStateFlow()
                ?: MutableStateFlow(emptySet())

        val ignoredThreads: kotlinx.coroutines.flow.Flow<List<com.narasimha.refire.data.database.IgnoredThreadEntity>>
            get() = instance?.ignoredRepository?.ignoredThreads
                ?: kotlinx.coroutines.flow.flowOf(emptyList())

        val ignoredCount: kotlinx.coroutines.flow.Flow<Int>
            get() = instance?.ignoredRepository?.ignoredCount
                ?: kotlinx.coroutines.flow.flowOf(0)

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

            // Try to extract persistable intent URI from notification extras
            val contentIntentUri = ContentIntentCache.tryExtractIntentUri(info.extras, info.packageName)

            val record = SnoozeRecord.fromNotification(info, endTime, contentIntentUri)

            // Store contentIntent for jump-back navigation (if available)
            // This is volatile but provides best precision if process survives
            ContentIntentCache.store(record.id, info.contentIntent)

            // Cancel ALL notifications for this thread (handles edge cases with multiple notifications)
            inst.cancelNotificationsForThread(info.getThreadIdentifier())

            inst.addSnoozeRecord(record)

            Log.i(TAG, "Snoozed notification: ${info.title} until $endTime (intentUri=${contentIntentUri != null})")
        }

        /**
         * Dismiss a notification from the in-app list.
         * Persists to history and removes from system tray.
         * For grouped notifications, dismisses ALL notifications in the thread.
         */
        fun dismissNotification(info: NotificationInfo) {
            val inst = instance ?: return
            val threadId = info.getThreadIdentifier()

            // Find all notifications in this thread (for grouped notifications)
            val notificationsInThread = inst._activeNotifications.value.filter {
                it.getThreadIdentifier() == threadId
            }

            // Immediately remove ALL from in-memory list
            inst._activeNotifications.value = inst._activeNotifications.value.filter {
                it.getThreadIdentifier() != threadId
            }

            // Persist to history as DISMISSED record (using the grouped info)
            inst.persistDismissedNotification(info)

            // Cancel ALL notifications in the thread from system tray
            notificationsInThread.forEach { notification ->
                inst.cancelNotificationSilently(notification.key)
            }

            Log.i(TAG, "Dismissed notification: ${info.title} (${notificationsInThread.size} in thread)")
        }

        /**
         * Dismiss ALL active notifications (excluding ignored threads).
         * Used for bulk "dismiss all" action.
         */
        fun dismissAllNotifications() {
            val inst = instance ?: return
            // Filter out ignored threads - they should remain in the system tray
            val allNotifications = inst._activeNotifications.value
                .filter { !inst.ignoredRepository.isIgnored(it.getThreadIdentifier()) }
                .toList()

            if (allNotifications.isEmpty()) return

            // Group by thread to get unique threads
            val byThread = allNotifications.groupBy { it.getThreadIdentifier() }

            // Clear the active list immediately (ignored threads already not in list)
            inst._activeNotifications.value = emptyList()

            // Persist each thread group to dismissed history and cancel from system tray
            byThread.forEach { (_, notifications) ->
                // Merge messages from all notifications in the thread
                val mergedMessages = mergeNotificationMessages(notifications)
                // Use most recent notification as representative, but with ALL merged messages
                val mostRecent = notifications.maxByOrNull { it.postTime } ?: notifications.first()
                val representative = mostRecent.copy(messages = mergedMessages)
                inst.persistDismissedNotification(representative)

                // Cancel all notifications in this thread from system tray
                notifications.forEach { notification ->
                    inst.cancelNotificationSilently(notification.key)
                }
            }

            Log.i(TAG, "Dismissed all notifications: ${allNotifications.size} total, ${byThread.size} threads")
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
            // Clean up cached contentIntent
            ContentIntentCache.remove(snoozeId)

            instance?.removeSnoozeRecord(snoozeId)
            Log.i(TAG, "Canceled snooze: $snoozeId")
        }

        /**
         * Dismiss an active snooze to history.
         * Cancels the alarm and moves to history with DISMISSED status.
         */
        fun dismissSnooze(snoozeId: String) {
            instance?.dismissSnoozeRecord(snoozeId)
            Log.i(TAG, "Dismissed snooze to history: $snoozeId")
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

        /**
         * Update the helper notification based on current settings.
         * Call this when settings change to re-evaluate show/hide state.
         */
        fun updateHelperNotification() {
            val inst = instance ?: return
            val count = inst._activeNotifications.value.size
            inst.helperManager.updateHelperNotification(count)
        }

        /**
         * Re-snooze a dismissed or history item.
         * Creates a new active snooze and removes the specific record.
         * Also cancels any live notifications for the same thread.
         */
        fun reSnoozeFromHistory(record: SnoozeRecord, endTime: LocalDateTime) {
            val inst = instance ?: return

            Log.d(TAG, "reSnoozeFromHistory: incoming record threadId='${record.threadId}', messages=${record.messages.size}")

            // Cancel any live notifications for this thread FIRST
            // This handles the edge case where new messages arrived after the history item was created
            inst.cancelNotificationsForThread(record.threadId)

            inst.serviceScope.launch {
                // Re-fetch the record from DB to get latest merged messages
                // The UI record may be stale if messages were merged after it was rendered
                val latestRecord = inst.repository.getSnoozeById(record.id) ?: record
                Log.d(TAG, "reSnoozeFromHistory: latestRecord messages=${latestRecord.messages.size}")

                // Create new snooze from the latest record
                val newRecord = latestRecord.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    snoozeEndTime = endTime,
                    createdAt = LocalDateTime.now(),
                    status = com.narasimha.refire.data.model.SnoozeStatus.ACTIVE,
                    suppressedCount = 0
                )

                Log.d(TAG, "reSnoozeFromHistory: newRecord messages=${newRecord.messages.size}")

                // Add new active snooze (atomic - replaces any existing active)
                inst.addSnoozeRecord(newRecord)

                // Delete only this specific record (not all history for the thread)
                inst.repository.deleteSnooze(record.id)

                Log.i(TAG, "Re-snoozed: ${record.title} until $endTime (${newRecord.messages.size} messages)")
            }
        }

        /**
         * Delete a history record.
         */
        fun deleteHistoryRecord(snoozeId: String) {
            instance?.deleteHistoryRecord(snoozeId)
            Log.i(TAG, "Deleted history record: $snoozeId")
        }

        /**
         * Restore a cancelled snooze (for undo functionality).
         * Re-inserts the record and reschedules the alarm.
         */
        fun restoreSnooze(record: SnoozeRecord) {
            instance?.addSnoozeRecord(record)
            Log.i(TAG, "Restored snooze: ${record.id}")
        }

        /**
         * Restore a deleted history record (for undo functionality).
         * Re-inserts the record without scheduling an alarm.
         */
        fun restoreHistoryRecord(record: SnoozeRecord) {
            instance?.restoreHistoryRecord(record)
            Log.i(TAG, "Restored history record: ${record.id}")
        }

        /**
         * Get a snooze record by ID (from active or history).
         * Used for re-snooze from notification action.
         */
        suspend fun getSnoozeById(snoozeId: String): SnoozeRecord? {
            return instance?.repository?.getSnoozeById(snoozeId)
        }

        /**
         * Ignore a notification thread or entire app.
         * Removes from active list, cancels any snooze, and deletes dismissed/history records.
         * @param info The notification to ignore
         * @param scope THREAD to ignore just this conversation, APP to ignore all from this app
         */
        fun ignoreThread(info: NotificationInfo, scope: IgnoreScope) {
            val inst = instance ?: return
            // When scope is APP, use packageName as threadId; otherwise use the thread identifier
            val threadId = if (scope == IgnoreScope.APP) info.packageName else info.getThreadIdentifier()
            val isPackageLevel = scope == IgnoreScope.APP

            inst.serviceScope.launch {
                // 1. Add to ignored repository
                inst.ignoredRepository.ignoreThread(
                    threadId = threadId,
                    packageName = info.packageName,
                    appName = info.appName,
                    displayTitle = if (isPackageLevel) info.appName else (info.title ?: info.appName),
                    isPackageLevel = isPackageLevel
                )

                // 2. Update StateFlow for UI
                inst._ignoredThreadIds.value = inst.ignoredRepository.getIgnoredIds()

                // 3. Remove from active notifications in-memory list
                // For APP scope, remove all notifications from this package
                inst._activeNotifications.value = inst._activeNotifications.value.filter {
                    if (isPackageLevel) {
                        it.packageName != info.packageName
                    } else {
                        it.getThreadIdentifier() != threadId
                    }
                }

                // 4. Cancel any active snoozes for this thread/app
                val activeSnoozesForThread = inst._snoozeRecords.value.filter {
                    val matches = if (isPackageLevel) {
                        it.packageName == info.packageName
                    } else {
                        it.threadId == threadId
                    }
                    matches && !it.isExpired()
                }
                activeSnoozesForThread.forEach { snooze ->
                    inst.alarmHelper.cancelSnoozeAlarm(snooze.id)
                    com.narasimha.refire.core.util.ContentIntentCache.remove(snooze.id)
                }

                // 5. Delete all snooze records (active, dismissed, expired) for this thread/app
                if (isPackageLevel) {
                    inst.repository.deleteByPackageName(info.packageName)
                } else {
                    inst.repository.deleteByThreadId(threadId)
                }

                // 6. Cancel notifications from system tray
                if (isPackageLevel) {
                    inst.cancelNotificationsForPackage(info.packageName)
                } else {
                    inst.cancelNotificationsForThread(threadId)
                }

                // 7. Update helper notification count
                inst.helperManager.updateHelperNotification(inst._activeNotifications.value.size)

                Log.i(TAG, "Ignored ${if (isPackageLevel) "app" else "thread"}: $threadId")
            }
        }

        /**
         * Ignore a snoozed record's thread or entire app.
         * @param record The snooze record to ignore
         * @param scope THREAD to ignore just this conversation, APP to ignore all from this app
         */
        fun ignoreSnoozeRecord(record: SnoozeRecord, scope: IgnoreScope) {
            val inst = instance ?: return
            // When scope is APP, use packageName as threadId; otherwise use the record's threadId
            val threadId = if (scope == IgnoreScope.APP) record.packageName else record.threadId
            val isPackageLevel = scope == IgnoreScope.APP

            inst.serviceScope.launch {
                // 1. Add to ignored repository
                inst.ignoredRepository.ignoreThread(
                    threadId = threadId,
                    packageName = record.packageName,
                    appName = record.appName,
                    displayTitle = if (isPackageLevel) record.appName else record.title,
                    isPackageLevel = isPackageLevel
                )

                // 2. Update StateFlow for UI
                inst._ignoredThreadIds.value = inst.ignoredRepository.getIgnoredIds()

                // 3. Remove from active notifications in-memory list
                // For APP scope, remove all notifications from this package
                inst._activeNotifications.value = inst._activeNotifications.value.filter {
                    if (isPackageLevel) {
                        it.packageName != record.packageName
                    } else {
                        it.getThreadIdentifier() != threadId
                    }
                }

                // 4. Cancel any active snoozes for this thread/app
                val activeSnoozesForThread = inst._snoozeRecords.value.filter {
                    val matches = if (isPackageLevel) {
                        it.packageName == record.packageName
                    } else {
                        it.threadId == threadId
                    }
                    matches && !it.isExpired()
                }
                activeSnoozesForThread.forEach { snooze ->
                    inst.alarmHelper.cancelSnoozeAlarm(snooze.id)
                    com.narasimha.refire.core.util.ContentIntentCache.remove(snooze.id)
                }

                // 5. Delete all snooze records for this thread/app
                if (isPackageLevel) {
                    inst.repository.deleteByPackageName(record.packageName)
                } else {
                    inst.repository.deleteByThreadId(threadId)
                }

                // 6. Cancel notifications from system tray
                if (isPackageLevel) {
                    inst.cancelNotificationsForPackage(record.packageName)
                } else {
                    inst.cancelNotificationsForThread(threadId)
                }

                // 7. Update helper notification count
                inst.helperManager.updateHelperNotification(inst._activeNotifications.value.size)

                Log.i(TAG, "Ignored ${if (isPackageLevel) "app" else "thread"}: $threadId")
            }
        }

        /**
         * Unignore a thread.
         */
        fun unignoreThread(threadId: String) {
            val inst = instance ?: return

            inst.serviceScope.launch {
                inst.ignoredRepository.unignoreThread(threadId)
                inst._ignoredThreadIds.value = inst.ignoredRepository.getIgnoredIds()

                // Refresh active notifications to include previously ignored ones
                inst.refreshActiveNotifications()

                Log.i(TAG, "Unignored thread: $threadId")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize database repository, alarm helper, and notification helper manager
        val database = com.narasimha.refire.data.database.ReFireDatabase.getInstance(applicationContext)
        repository = com.narasimha.refire.data.repository.SnoozeRepository(database.snoozeDao())
        ignoredRepository = com.narasimha.refire.data.repository.IgnoredThreadRepository(database.ignoredThreadDao())
        alarmHelper = com.narasimha.refire.core.util.AlarmManagerHelper(applicationContext)
        helperManager = com.narasimha.refire.core.util.NotificationHelperManager(applicationContext)

        // Register as foreground callback so helper notification uses startForeground
        helperManager.setForegroundCallback(this)

        Log.i(TAG, "NotificationListenerService created")
    }

    // ForegroundCallback implementation
    override fun onStartForeground(notificationId: Int, notification: Notification) {
        if (!isInForeground) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceCompat.startForeground(
                        this,
                        notificationId,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(notificationId, notification)
                }
                isInForeground = true
                Log.i(TAG, "Started foreground service with helper notification")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start foreground service", e)
            }
        } else {
            // Already in foreground, just update the notification
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.notify(notificationId, notification)
            Log.d(TAG, "Updated foreground notification")
        }
    }

    override fun onStopForeground() {
        if (isInForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            isInForeground = false
            Log.i(TAG, "Stopped foreground service")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
        // Clear callback before cancelling to avoid callback after service is destroyed
        helperManager.setForegroundCallback(null)
        helperManager.cancelHelperNotification()
        Log.i(TAG, "NotificationListenerService destroyed")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Listener connected - notification access active")

        // Initialize ignored thread cache first
        serviceScope.launch {
            ignoredRepository.refreshCache()
            _ignoredThreadIds.value = ignoredRepository.getIgnoredIds()
            Log.d(TAG, "Loaded ${_ignoredThreadIds.value.size} ignored threads")
        }

        // Populate initial active notifications
        refreshActiveNotifications()

        // Load persisted snoozes from database
        serviceScope.launch {
            repository.activeSnoozes.collect { records ->
                _snoozeRecords.value = records
            }
        }

        // Load history (expired) snoozes from database
        serviceScope.launch {
            repository.historySnoozes.collect { records ->
                _historySnoozes.value = records
            }
        }

        // Load dismissed notifications from database (for LIVE tab)
        serviceScope.launch {
            repository.dismissedSnoozes.collect { records ->
                _dismissedRecords.value = records
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

        // Skip if thread is ignored
        if (ignoredRepository.isIgnored(threadId)) {
            Log.d(TAG, "Skipping notification for ignored thread: $threadId")
            return
        }

        Log.d(TAG, "Notification posted: ${info.packageName} | Thread: $threadId | Title: ${info.title} | Text: ${info.text} | Messages: ${info.messages.size} | GroupSummary: $isGroupSummary | GroupKey: ${info.groupKey} | ShortcutId: ${info.shortcutId}")

        // Check if this thread is snoozed
        val snoozedRecord = _snoozeRecords.value.find {
            it.threadId == threadId && !it.isExpired()
        }

        if (snoozedRecord != null) {
            Log.i(TAG, "Canceling notification for snoozed thread: $threadId")
            cancelNotificationSilently(sbn.key)

            // Accumulate suppressed messages into the snooze record
            if (info.messages.isNotEmpty()) {
                serviceScope.launch {
                    repository.appendSuppressedMessages(snoozedRecord.id, info.messages)
                    Log.d(TAG, "Appended ${info.messages.size} suppressed messages to snooze: ${snoozedRecord.id}")
                }
            }

            serviceScope.launch {
                _notificationEvents.emit(NotificationEvent.NotificationSuppressed(info))
            }
            return
        }

        // Update active notifications list first
        refreshActiveNotifications()

        // Check for DISMISSED records to potentially merge back into Active
        // Only merge recent dismissed - stale ones are deleted (new notification starts fresh lifecycle)
        val dismissedForThread = _dismissedRecords.value.filter { it.threadId == threadId }
        if (dismissedForThread.isNotEmpty()) {
            val now = LocalDateTime.now()
            val mergeThresholdHours = RetentionPreferences.getInstance(applicationContext)
                .dismissedRetentionHours.value.toLong()

            // Partition into recent (merge) vs stale (delete without merging)
            val (recentDismissed, staleDismissed) = dismissedForThread.partition { record ->
                val hoursSinceDismissed = java.time.Duration.between(record.snoozeEndTime, now).toHours()
                hoursSinceDismissed < mergeThresholdHours
            }

            // Delete stale dismissed records - lifecycle ended, new notification is more relevant
            if (staleDismissed.isNotEmpty()) {
                serviceScope.launch {
                    staleDismissed.forEach { record ->
                        repository.deleteSnooze(record.id)
                    }
                    Log.d(TAG, "Deleted ${staleDismissed.size} stale DISMISSED records (>${mergeThresholdHours}h old) for thread: $threadId")
                }
            }

            // Only merge recent dismissed messages
            if (recentDismissed.isNotEmpty()) {
                val dismissedMessages = recentDismissed.flatMap { it.messages }

                // Merge dismissed messages into active notification for this thread
                _activeNotifications.value = _activeNotifications.value.map { notification ->
                    if (notification.getThreadIdentifier() == threadId) {
                        val mergedMessages = (dismissedMessages + notification.messages)
                            .distinctBy { "${it.sender.trim()}|${it.text.trim()}" }
                            .sortedByDescending { it.timestamp }
                        notification.copy(messages = mergedMessages)
                    } else {
                        notification
                    }
                }

                // Delete the recent dismissed records after merging
                serviceScope.launch {
                    recentDismissed.forEach { record ->
                        repository.deleteSnooze(record.id)
                    }
                }
            }
        }

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

        // Add to recents if dismissed by user action or app auto-dismiss (e.g., WhatsApp dismisses when you open it)
        // IMPORTANT: Do this BEFORE refreshActiveNotifications() so we can still access children
        val isUserDismissalOrAppCancel = reason == REASON_CANCEL ||
                                         reason == REASON_CANCEL_ALL ||
                                         reason == REASON_CLICK ||
                                         reason == REASON_APP_CANCEL ||
                                         reason == REASON_APP_CANCEL_ALL

        if (isUserDismissalOrAppCancel) {
            if (isGroupSummary) {
                // Group was dismissed - persist each child individually (not the summary)
                // First try system's activeNotifications, fall back to our cache if empty
                // (system may have already removed children by the time we get this callback)
                val groupKey = sbn.groupKey
                val childrenSbn = activeNotifications.filter { childSbn ->
                    childSbn.groupKey == groupKey &&
                    childSbn.key != sbn.key &&
                    !shouldIgnoreNotification(childSbn)
                }

                if (childrenSbn.isNotEmpty()) {
                    // Use fresh data from system
                    childrenSbn.forEach { childSbn ->
                        val childInfo = NotificationInfo.fromStatusBarNotification(childSbn, applicationContext)
                        persistDismissedNotification(childInfo)
                        Log.d(TAG, "Persisted child to history: ${childInfo.title} | messages: ${childInfo.messages.size}")
                    }
                    Log.d(TAG, "Expanded group into ${childrenSbn.size} individual history records (from system)")
                } else {
                    // System already cleared children - use our cached data
                    val cachedChildren = _activeNotifications.value.filter { cached ->
                        cached.groupKey == groupKey && cached.key != sbn.key
                    }
                    cachedChildren.forEach { childInfo ->
                        persistDismissedNotification(childInfo)
                        Log.d(TAG, "Persisted cached child to history: ${childInfo.title} | messages: ${childInfo.messages.size}")
                    }
                    Log.d(TAG, "Expanded group into ${cachedChildren.size} individual history records (from cache)")
                }
            } else {
                // Individual notification - persist as-is
                persistDismissedNotification(info)
                Log.d(TAG, "Persisted individual to history: ${info.packageName} | ${info.title}")
            }

            serviceScope.launch {
                _notificationEvents.emit(NotificationEvent.NotificationDismissed(info))
            }
        }

        // Update active notifications list
        refreshActiveNotifications()
    }

    /**
     * Refresh the active notifications list from system.
     * Filters out group summary notifications when child notifications exist.
     * Filters out ignored threads.
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

            // Filter out group summaries when children exist, and filter out ignored threads
            val notifications = rawNotifications
                .filter { sbn ->
                    val isGroupSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
                    // Keep if: not a summary, OR if summary but no children in this group
                    !isGroupSummary || sbn.groupKey !in groupsWithChildren
                }
                .map { sbn ->
                    NotificationInfo.fromStatusBarNotification(sbn, applicationContext)
                }
                .filter { info ->
                    // Filter out ignored threads
                    !ignoredRepository.isIgnored(info.getThreadIdentifier())
                }
                .sortedByDescending { it.postTime }

            _activeNotifications.value = notifications
            Log.d(TAG, "Refreshed active notifications: ${notifications.size} items (filtered ${rawNotifications.size - notifications.size} group summaries/ignored)")

            // Update the helper notification based on current count
            helperManager.updateHelperNotification(notifications.size)
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

    private fun dismissSnoozeRecord(snoozeId: String) {
        serviceScope.launch {
            // Cancel scheduled alarm
            alarmHelper.cancelSnoozeAlarm(snoozeId)

            // Update status to DISMISSED (moves to history)
            repository.markAsDismissed(snoozeId)

            Log.i(TAG, "Dismissed snooze $snoozeId to history")
        }
    }

    private fun deleteHistoryRecord(snoozeId: String) {
        serviceScope.launch {
            repository.deleteSnooze(snoozeId)
            Log.d(TAG, "Deleted history record: $snoozeId")
        }
    }

    private fun restoreHistoryRecord(record: SnoozeRecord) {
        serviceScope.launch {
            // Re-insert to database (no alarm needed for expired snoozes)
            repository.insertSnooze(record)
            Log.d(TAG, "Restored history record: ${record.id}")
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

    /**
     * Cancel all notifications for a given thread ID.
     * This ensures the thread only exists in one state (Live, Scheduled, or History).
     */
    private fun cancelNotificationsForThread(threadId: String) {
        // Find all active notifications matching this threadId
        val matching = _activeNotifications.value.filter {
            it.getThreadIdentifier() == threadId
        }

        // Cancel each notification from system tray
        matching.forEach { notification ->
            cancelNotificationSilently(notification.key)
            Log.d(TAG, "Canceled notification for thread $threadId: ${notification.key}")
        }

        // Remove from in-memory list immediately (don't wait for onNotificationRemoved)
        if (matching.isNotEmpty()) {
            _activeNotifications.value = _activeNotifications.value.filter {
                it.getThreadIdentifier() != threadId
            }
            Log.i(TAG, "Removed ${matching.size} notifications for thread: $threadId")
        }
    }

    /**
     * Cancel all notifications for a given package name.
     * Used when ignoring an entire app.
     */
    private fun cancelNotificationsForPackage(packageName: String) {
        // Find all active notifications matching this package
        val matching = _activeNotifications.value.filter {
            it.packageName == packageName
        }

        // Cancel each notification from system tray
        matching.forEach { notification ->
            cancelNotificationSilently(notification.key)
            Log.d(TAG, "Canceled notification for package $packageName: ${notification.key}")
        }

        // Remove from in-memory list immediately (don't wait for onNotificationRemoved)
        if (matching.isNotEmpty()) {
            _activeNotifications.value = _activeNotifications.value.filter {
                it.packageName != packageName
            }
            Log.i(TAG, "Removed ${matching.size} notifications for package: $packageName")
        }
    }

    private fun persistDismissedNotification(info: NotificationInfo) {
        serviceScope.launch {
            // Check if we have a grouped version with more messages
            // This handles system tray dismissal where Android only provides single notification data
            val threadId = info.getThreadIdentifier()
            val groupedInfo = _activeNotifications.value.find {
                it.getThreadIdentifier() == threadId
            }

            // Use grouped messages if available (they're already merged from groupNotificationsByThread)
            val recordInfo = if (groupedInfo != null && groupedInfo.messages.size > info.messages.size) {
                info.copy(messages = groupedInfo.messages)
            } else {
                info
            }

            // Filter out messages that already exist in EXPIRED history for this thread
            // This prevents overlap between Dismissed and History sections
            val expiredMessageKeys = repository.getExpiredMessageKeysForThread(threadId)
            val filteredMessages = recordInfo.messages.filter {
                "${it.sender.trim()}|${it.text.trim()}" !in expiredMessageKeys
            }

            // If all messages are duplicates of history, skip creating DISMISSED record
            if (filteredMessages.isEmpty() && recordInfo.messages.isNotEmpty()) {
                Log.d(TAG, "Skipping dismissed notification - all ${recordInfo.messages.size} messages already in history: ${info.title}")
                return@launch
            }

            // Create record with filtered messages (only truly new ones)
            val finalRecordInfo = if (filteredMessages.size < recordInfo.messages.size) {
                recordInfo.copy(messages = filteredMessages)
            } else {
                recordInfo
            }

            val record = finalRecordInfo.toDismissedRecord()
            // Use insertOrMergeHistory to consolidate with existing history for same thread
            repository.insertOrMergeHistory(record)
            Log.d(TAG, "Persisted dismissed notification to history: ${info.title} | messages: ${record.messages.size} (filtered from ${recordInfo.messages.size})")
        }
    }

    private fun shouldIgnoreNotification(sbn: StatusBarNotification): Boolean {
        // 1. Ignore our own notifications
        if (sbn.packageName == packageName) return true

        // 2. Block common system packages
        val systemPackages = setOf(
            "android",
            "com.google.android.gms",
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
