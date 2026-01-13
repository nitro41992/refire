package com.narasimha.refire.core.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.narasimha.refire.service.SnoozeAlarmReceiver

/**
 * Helper for scheduling and canceling AlarmManager alarms for snooze expiration.
 */
class AlarmManagerHelper(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedule exact alarm for snooze expiration.
     * Uses setExactAndAllowWhileIdle for battery efficiency + Doze mode support.
     */
    fun scheduleSnoozeAlarm(snoozeId: String, triggerTimeMillis: Long) {
        val intent = Intent(context, SnoozeAlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE_EXPIRED
            putExtra(EXTRA_SNOOZE_ID, snoozeId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            snoozeId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTimeMillis,
            pendingIntent
        )
    }

    /**
     * Cancel scheduled alarm for a snooze.
     */
    fun cancelSnoozeAlarm(snoozeId: String) {
        val intent = Intent(context, SnoozeAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            snoozeId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    companion object {
        const val ACTION_SNOOZE_EXPIRED = "com.narasimha.refire.SNOOZE_EXPIRED"
        const val EXTRA_SNOOZE_ID = "snooze_id"
    }
}
