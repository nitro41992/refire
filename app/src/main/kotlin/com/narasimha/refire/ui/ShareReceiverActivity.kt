package com.narasimha.refire.ui

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.narasimha.refire.R
import com.narasimha.refire.data.model.SharedContent
import com.narasimha.refire.data.model.SnoozePreset
import com.narasimha.refire.data.model.SnoozeRecord
import com.narasimha.refire.service.ReFireNotificationListener
import com.narasimha.refire.ui.components.SnoozeBottomSheet
import com.narasimha.refire.ui.theme.ReFireTheme
import java.time.format.DateTimeFormatter

/**
 * Transparent activity that receives shared content and displays bottom sheet.
 */
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedContent = SharedContent.fromIntent(intent)

        if (sharedContent == null) {
            Toast.makeText(this, R.string.error_unable_to_process, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            ReFireTheme {
                ShareReceiverContent(
                    sharedContent = sharedContent,
                    onSnoozeConfirmed = { preset ->
                        handleSnoozeConfirmed(sharedContent, preset)
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }

    private fun handleSnoozeConfirmed(content: SharedContent, preset: SnoozePreset) {
        val endTime = preset.calculateEndTime()

        // Create snooze record and add to stash
        val record = SnoozeRecord.fromSharedContent(content, endTime)
        ReFireNotificationListener.addSnoozeFromShareSheet(record)

        val formattedTime = DateTimeFormatter
            .ofPattern("h:mm a")
            .format(endTime)

        Toast.makeText(
            this,
            getString(R.string.snooze_confirm, formattedTime),
            Toast.LENGTH_SHORT
        ).show()

        Log.i(
            "ShareReceiver",
            "Snooze created: ${content.getDisplayTitle()} until $endTime"
        )

        finish()
    }
}

@Composable
private fun ShareReceiverContent(
    sharedContent: SharedContent,
    onSnoozeConfirmed: (SnoozePreset) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)
    ) {
        var isLoading by remember { mutableStateOf(false) }

        // TODO: Phase 2 - Trigger Open Graph scraping for URLs

        SnoozeBottomSheet(
            sharedContent = sharedContent,
            isLoading = isLoading,
            onSnoozeSelected = onSnoozeConfirmed,
            onDismiss = onDismiss
        )
    }
}
