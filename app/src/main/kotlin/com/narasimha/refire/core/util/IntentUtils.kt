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
     * Build jump-back intent with fallback chain.
     *
     * Note: For notification-sourced snoozes, the primary navigation mechanism is
     * the cached PendingIntent in ContentIntentCache (handled in SnoozeAlarmReceiver).
     * This method provides fallback when that cache is unavailable (process killed).
     *
     * Strategy chain (in order of preference):
     * 1. Shared URLs → ACTION_VIEW with URL data
     * 2. Persisted contentIntentUri → Reconstruct Intent from URI (rarely works, but zero cost)
     * 3. Fallback → App launcher intent
     */
    fun buildJumpBackIntent(context: Context, snooze: SnoozeRecord): Intent {
        // Strategy 1: Handle shared URLs with ACTION_VIEW for proper deep-linking
        if (snooze.source == SnoozeSource.SHARE_SHEET &&
            snooze.contentType == "URL" &&
            !snooze.text.isNullOrBlank()) {
            try {
                Log.d(TAG, "Building ACTION_VIEW intent for shared URL: ${snooze.text}")
                return Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse(snooze.text)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create ACTION_VIEW intent for URL: ${snooze.text}", e)
            }
        }

        // Strategy 2: Try persisted contentIntentUri (rarely works, but costs nothing to try)
        if (!snooze.contentIntentUri.isNullOrBlank()) {
            try {
                Log.d(TAG, "Attempting to reconstruct intent from persisted URI for ${snooze.packageName}")
                val intent = Intent.parseUri(snooze.contentIntentUri, Intent.URI_INTENT_SCHEME)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                Log.i(TAG, "Successfully reconstructed intent from URI for ${snooze.packageName}")
                return intent
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse persisted contentIntentUri: ${e.message}")
            }
        }

        // Strategy 3: Fallback to app launcher (opens main view)
        Log.d(TAG, "Using app launcher fallback for ${snooze.packageName}")
        return buildLauncherIntent(context, snooze.packageName)
    }

    /**
     * Build a launcher intent for the given package.
     */
    private fun buildLauncherIntent(context: Context, packageName: String): Intent {
        return context.packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent().apply {
                setPackage(packageName)
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
