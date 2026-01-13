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
import com.narasimha.refire.data.database.ReFireDatabase
import com.narasimha.refire.data.model.SnoozeRecord
import com.narasimha.refire.data.model.SnoozeSource
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

            // Remove from database
            repository.deleteSnooze(snoozeId)
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

            // Build jump-back intent with deep-linking
            val jumpIntent = buildJumpBackIntent(context, snooze)
            val pendingIntent = PendingIntent.getActivity(
                context,
                snooze.id.hashCode(),
                jumpIntent,
                PendingIntent.FLAG_IMMUTABLE
            )

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

    /**
     * Build jump-back intent with deep-linking attempt.
     * Strategy: Try shortcut-based deep link, fallback to app launcher.
     */
    private fun buildJumpBackIntent(context: Context, snooze: SnoozeRecord): Intent {
        // Attempt deep-linking for notification-sourced snoozes
        if (snooze.source == SnoozeSource.NOTIFICATION && snooze.shortcutId != null) {
            try {
                val shortcutIntent = Intent(Intent.ACTION_VIEW).apply {
                    setPackage(snooze.packageName)
                    putExtra("shortcut_id", snooze.shortcutId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }

                // Verify the intent resolves
                if (context.packageManager.queryIntentActivities(shortcutIntent, 0).isNotEmpty()) {
                    return shortcutIntent
                }
            } catch (e: Exception) {
                // Fall through to launcher intent
            }
        }

        // Fallback: Open app's main launcher
        return context.packageManager.getLaunchIntentForPackage(snooze.packageName)
            ?: Intent().apply {
                setPackage(snooze.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }

}
