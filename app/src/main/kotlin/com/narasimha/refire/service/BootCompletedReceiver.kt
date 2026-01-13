package com.narasimha.refire.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.narasimha.refire.core.util.AlarmManagerHelper
import com.narasimha.refire.data.database.ReFireDatabase
import com.narasimha.refire.data.repository.SnoozeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restores scheduled alarms after device reboot.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        CoroutineScope(Dispatchers.IO).launch {
            val database = ReFireDatabase.getInstance(context)
            val repository = SnoozeRepository(database.snoozeDao())
            val alarmHelper = AlarmManagerHelper(context)

            // Get all active snoozes
            val snoozes = repository.getAllSnoozes()

            val now = System.currentTimeMillis()

            snoozes.forEach { snooze ->
                val triggerTime = snooze.toEpochMillis()

                if (triggerTime > now) {
                    // Future snooze - reschedule alarm
                    alarmHelper.scheduleSnoozeAlarm(snooze.id, triggerTime)
                } else {
                    // Expired during reboot - fire immediately
                    val expiredIntent = Intent(context, SnoozeAlarmReceiver::class.java).apply {
                        action = AlarmManagerHelper.ACTION_SNOOZE_EXPIRED
                        putExtra(AlarmManagerHelper.EXTRA_SNOOZE_ID, snooze.id)
                    }
                    context.sendBroadcast(expiredIntent)
                }
            }
        }
    }
}
