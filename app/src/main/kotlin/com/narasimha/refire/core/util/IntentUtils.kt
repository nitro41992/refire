package com.narasimha.refire.core.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.narasimha.refire.data.model.SnoozeRecord
import com.narasimha.refire.data.model.SnoozeSource

/**
 * Utilities for building intents to launch apps and URLs.
 */
object IntentUtils {
    private const val TAG = "IntentUtils"

    /**
     * Build jump-back intent with deep-linking attempt.
     * Strategy: Try shared URL → shortcut-based deep link → app launcher.
     */
    fun buildJumpBackIntent(context: Context, snooze: SnoozeRecord): Intent {
        // Handle shared URLs with ACTION_VIEW for proper deep-linking
        if (snooze.source == SnoozeSource.SHARE_SHEET &&
            snooze.contentType == "URL" &&
            !snooze.text.isNullOrBlank()) {
            try {
                return Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse(snooze.text)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create ACTION_VIEW intent for URL: ${snooze.text}", e)
                // Fall through to launcher intent
            }
        }

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
                Log.w(TAG, "Shortcut intent failed, falling back to launcher", e)
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

    /**
     * Launch the appropriate app or URL for a snooze record.
     * Returns true if launch was successful, false otherwise.
     */
    fun launchSnooze(context: Context, snooze: SnoozeRecord): Boolean {
        return try {
            val intent = buildJumpBackIntent(context, snooze)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch intent for snooze: ${snooze.id}", e)
            false
        }
    }
}
