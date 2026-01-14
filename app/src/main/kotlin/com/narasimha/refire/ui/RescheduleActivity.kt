package com.narasimha.refire.ui

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.narasimha.refire.R
import com.narasimha.refire.data.model.SnoozePreset
import com.narasimha.refire.data.model.SnoozeRecord
import com.narasimha.refire.service.ReFireNotificationListener
import com.narasimha.refire.service.SnoozeAlarmReceiver
import com.narasimha.refire.ui.components.SnoozeBottomSheet
import com.narasimha.refire.ui.theme.ReFireTheme
import java.time.format.DateTimeFormatter

/**
 * Transparent activity that displays reschedule bottom sheet from notification action.
 * Shows only the bottom sheet over a dimmed background (like TickTick).
 */
class RescheduleActivity : ComponentActivity() {

    private var snoozeId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        snoozeId = intent.getStringExtra(SnoozeAlarmReceiver.EXTRA_SNOOZE_ID)

        if (snoozeId == null) {
            Log.w(TAG, "No snooze ID provided")
            finish()
            return
        }

        // Enable edge-to-edge for smooth keyboard animations
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ReFireTheme {
                RescheduleContent(
                    snoozeId = snoozeId!!,
                    onRescheduleConfirmed = { record, preset ->
                        handleRescheduleConfirmed(record, preset)
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }

    private fun handleRescheduleConfirmed(record: SnoozeRecord, preset: SnoozePreset) {
        val endTime = preset.calculateEndTime()

        // Re-snooze from history
        ReFireNotificationListener.reSnoozeFromHistory(record, endTime)

        // Dismiss the re-fire notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(record.id.hashCode())

        val formattedTime = DateTimeFormatter
            .ofPattern("h:mm a")
            .format(endTime)

        Toast.makeText(
            this,
            getString(R.string.snooze_confirm, formattedTime),
            Toast.LENGTH_SHORT
        ).show()

        Log.i(TAG, "Rescheduled: ${record.title} until $endTime")

        finish()
    }

    companion object {
        private const val TAG = "RescheduleActivity"
    }
}

@Composable
private fun RescheduleContent(
    snoozeId: String,
    onRescheduleConfirmed: (SnoozeRecord, SnoozePreset) -> Unit,
    onDismiss: () -> Unit
) {
    var snoozeRecord by remember { mutableStateOf<SnoozeRecord?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Load snooze record
    LaunchedEffect(snoozeId) {
        snoozeRecord = ReFireNotificationListener.getSnoozeById(snoozeId)
        isLoading = false

        if (snoozeRecord == null) {
            Log.w("RescheduleContent", "Snooze record not found: $snoozeId")
            onDismiss()
        }
    }

    snoozeRecord?.let { record ->
        SnoozeBottomSheet(
            snoozeRecord = record,
            isLoading = isLoading,
            onSnoozeSelected = { preset ->
                onRescheduleConfirmed(record, preset)
            },
            onDismiss = onDismiss
        )
    }
}
