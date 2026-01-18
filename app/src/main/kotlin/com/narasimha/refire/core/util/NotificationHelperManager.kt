package com.narasimha.refire.core.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.narasimha.refire.R
import com.narasimha.refire.data.preferences.HelperPreferences
import com.narasimha.refire.service.HelperActionReceiver
import com.narasimha.refire.ui.MainActivity

/**
 * Manages the persistent "notification helper" that appears when
 * active notification count exceeds the user-configured threshold.
 */
class NotificationHelperManager(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
        as NotificationManager
    private val preferences = HelperPreferences.getInstance(context)

    /**
     * Updates the helper notification based on current active count.
     * Shows notification if count >= threshold and helper is enabled.
     * Hides notification if count < threshold or helper is disabled.
     */
    fun updateHelperNotification(activeCount: Int) {
        val isEnabled = preferences.isHelperEnabled.value
        val threshold = preferences.helperThreshold.value

        Log.d(TAG, "updateHelperNotification: count=$activeCount, enabled=$isEnabled, threshold=$threshold")

        if (!isEnabled || activeCount < threshold) {
            cancelHelperNotification()
            return
        }

        showHelperNotification(activeCount)
    }

    /**
     * Force cancel the helper notification.
     * Called when user disables the feature or service is destroyed.
     */
    fun cancelHelperNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
        Log.d(TAG, "Helper notification cancelled")
    }

    private fun showHelperNotification(count: Int) {
        // Content intent - tap to open app
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_CONTENT,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss All action
        val dismissAllIntent = Intent(context, HelperActionReceiver::class.java).apply {
            action = HelperActionReceiver.ACTION_DISMISS_ALL
        }
        val dismissAllPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_DISMISS_ALL,
            dismissAllIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Clear & Schedule action - use PendingIntent.getActivity directly to bypass
        // Android 10+ restrictions on starting activities from BroadcastReceivers
        val clearScheduleIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_NAVIGATE_TO_DISMISSED, true)
            putExtra(MainActivity.EXTRA_DISMISS_ALL, true)
        }
        val clearSchedulePendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_CLEAR_SCHEDULE,
            clearScheduleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.helper_notification_title, count))
            .setContentText(context.getString(R.string.helper_notification_text))
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, context.getString(R.string.helper_action_dismiss_all), dismissAllPendingIntent)
            .addAction(0, context.getString(R.string.helper_action_clear_schedule), clearSchedulePendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "Helper notification shown: $count notifications")
    }

    companion object {
        private const val TAG = "NotificationHelper"
        const val CHANNEL_ID = "helper_channel"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_CODE_CONTENT = 2001
        private const val REQUEST_CODE_DISMISS_ALL = 2002
        private const val REQUEST_CODE_CLEAR_SCHEDULE = 2003
    }
}
