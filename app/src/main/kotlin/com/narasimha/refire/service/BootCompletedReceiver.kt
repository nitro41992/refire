package com.narasimha.refire.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.narasimha.refire.core.util.AlarmManagerHelper
import com.narasimha.refire.data.database.ReFireDatabase
import com.narasimha.refire.data.model.SnoozeStatus
import com.narasimha.refire.data.repository.SnoozeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restores scheduled alarms after device reboot.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        CoroutineScope(Dispatchers.IO).launch {
            val database = ReFireDatabase.getInstance(context)
            val repository = SnoozeRepository(database.snoozeDao())
            val alarmHelper = AlarmManagerHelper(context)

            // Only process ACTIVE snoozes - EXPIRED/DISMISSED records don't need alarm restoration
            val snoozes = repository.getAllSnoozes().filter { it.status == SnoozeStatus.ACTIVE }

            Log.d(TAG, "Boot completed: processing ${snoozes.size} ACTIVE snoozes")

            val now = System.currentTimeMillis()

            snoozes.forEach { snooze ->
                val triggerTime = snooze.toEpochMillis()

                if (triggerTime > now) {
                    // Future snooze - reschedule alarm
                    alarmHelper.scheduleSnoozeAlarm(snooze.id, triggerTime)
                    Log.d(TAG, "Rescheduled alarm for snooze: ${snooze.id} (${snooze.title})")
                } else {
                    // Expired during reboot - fire immediately
                    val expiredIntent = Intent(context, SnoozeAlarmReceiver::class.java).apply {
                        action = AlarmManagerHelper.ACTION_SNOOZE_EXPIRED
                        putExtra(AlarmManagerHelper.EXTRA_SNOOZE_ID, snooze.id)
                    }
                    context.sendBroadcast(expiredIntent)
                    Log.d(TAG, "Fired expired snooze: ${snooze.id} (${snooze.title})")
                }
            }
        }
    }
}
