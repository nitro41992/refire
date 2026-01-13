package com.narasimha.refire.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.narasimha.refire.R
import com.narasimha.refire.core.util.AlarmManagerHelper
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

            val notification = NotificationCompat.Builder(context, com.narasimha.refire.ReFire.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(snooze.title)
                .setContentText("from ${snooze.appName}")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            notificationManager.notify(snooze.id.hashCode(), notification)
            android.util.Log.i(TAG, "Posted re-fire notification for snooze: ${snooze.id}")

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to post re-fire notification", e)
        }
    }

    companion object {
        private const val TAG = "SnoozeAlarmReceiver"
    }
}
