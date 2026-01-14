package com.narasimha.refire.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import com.narasimha.refire.R
import com.narasimha.refire.core.util.AlarmManagerHelper
import com.narasimha.refire.ui.RescheduleActivity
import com.narasimha.refire.core.util.ContentIntentCache
import com.narasimha.refire.core.util.IntentUtils
import com.narasimha.refire.data.database.ReFireDatabase
import com.narasimha.refire.data.model.SnoozeRecord
import com.narasimha.refire.data.repository.SnoozeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives alarm when snooze expires and posts re-fire notification.
 */
class SnoozeAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmManagerHelper.ACTION_SNOOZE_EXPIRED) return

        val snoozeId = intent.getStringExtra(AlarmManagerHelper.EXTRA_SNOOZE_ID) ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val database = ReFireDatabase.getInstance(context)
            val repository = SnoozeRepository(database.snoozeDao())

            // Get snooze record
            val snooze = repository.getSnoozeById(snoozeId) ?: return@launch

            // Post re-fire notification
            postReFireNotification(context, snooze)

            // Mark as expired (move to history) instead of deleting
            repository.markAsExpired(snoozeId)

            // Cleanup old history entries (older than 7 days)
            repository.cleanupOldHistory()
        }
    }

    private fun postReFireNotification(context: Context, snooze: SnoozeRecord) {
        // Check permission before posting (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                android.util.Log.e(TAG, "POST_NOTIFICATIONS permission not granted. Cannot post re-fire notification.")
                return
            }
        }

        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

            // Try cached contentIntent first (exact navigation to conversation)
            val cachedIntent = ContentIntentCache.get(snooze.id)
            val pendingIntent = if (cachedIntent != null) {
                android.util.Log.d(TAG, "Using cached contentIntent for jump-back: ${snooze.id}")
                // Clean up cache after retrieval
                ContentIntentCache.remove(snooze.id)
                cachedIntent
            } else {
                // Fallback to constructed intent
                android.util.Log.d(TAG, "No cached contentIntent, using fallback for: ${snooze.id}")
                val jumpIntent = IntentUtils.buildJumpBackIntent(context, snooze)
                PendingIntent.getActivity(
                    context,
                    snooze.id.hashCode(),
                    jumpIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

            val builder = NotificationCompat.Builder(context, com.narasimha.refire.ReFire.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(snooze.title)
                .setSubText(snooze.appName)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

            // Add large icon (source app icon)
            try {
                val appIcon = context.packageManager.getApplicationIcon(snooze.packageName)
                builder.setLargeIcon(appIcon.toBitmap())
            } catch (e: Exception) {
                // App may be uninstalled - skip large icon
                android.util.Log.w(TAG, "Could not load app icon for ${snooze.packageName}")
            }

            // Add message content using appropriate style
            if (snooze.messages.size == 1) {
                // Single message - use BigTextStyle for text wrapping
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(snooze.messages.first().text))
            } else if (snooze.messages.size > 1) {
                // Multiple messages - use InboxStyle for list view
                val style = NotificationCompat.InboxStyle()
                snooze.messages.take(5).forEach { message ->
                    style.addLine(message.text)
                }
                if (snooze.messages.size > 5) {
                    style.setSummaryText("+${snooze.messages.size - 5} more")
                }
                builder.setStyle(style)
            } else if (!snooze.text.isNullOrBlank()) {
                // Fallback single text - use BigTextStyle
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(snooze.text))
            } else {
                // Fallback to simple content text
                builder.setContentText("Tap to open in ${snooze.appName}")
            }

            // Add "Reschedule" action button to re-snooze from notification
            val rescheduleIntent = Intent(context, RescheduleActivity::class.java).apply {
                action = ACTION_RESCHEDULE
                putExtra(EXTRA_SNOOZE_ID, snooze.id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val reschedulePendingIntent = PendingIntent.getActivity(
                context,
                snooze.id.hashCode() + 1,
                rescheduleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Reschedule", reschedulePendingIntent)

            val notification = builder.build()
            notificationManager.notify(snooze.id.hashCode(), notification)
            android.util.Log.i(TAG, "Posted rich re-fire notification for snooze: ${snooze.id} (${snooze.messages.size} messages)")

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to post re-fire notification", e)
        }
    }

    companion object {
        private const val TAG = "SnoozeAlarmReceiver"
        const val ACTION_RESCHEDULE = "com.narasimha.refire.ACTION_RESCHEDULE"
        const val EXTRA_SNOOZE_ID = "snooze_id"
    }
}
