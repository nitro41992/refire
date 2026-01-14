package com.narasimha.refire.core.util

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log

/**
 * In-memory cache for notification contentIntents.
 * Used for jump-back navigation when snoozes expire.
 *
 * Note: This cache is cleared on app restart. Snoozes that span
 * app restarts will fall back to ShortcutManager or launcher intent.
 */
object ContentIntentCache {
    private const val TAG = "ContentIntentCache"
    private val cache = mutableMapOf<String, PendingIntent>()

    fun store(snoozeId: String, pendingIntent: PendingIntent?) {
        if (pendingIntent != null) {
            cache[snoozeId] = pendingIntent
            Log.d(TAG, "Stored contentIntent for snooze: $snoozeId")
        }
    }

    fun get(snoozeId: String): PendingIntent? {
        return cache[snoozeId]
    }

    fun remove(snoozeId: String) {
        cache.remove(snoozeId)
        Log.d(TAG, "Removed contentIntent for snooze: $snoozeId")
    }

    fun clear() {
        cache.clear()
    }

    /**
     * Attempt to extract a persistable intent URI from notification extras.
     * Some apps include deep-link data in notification extras that we can persist.
     *
     * @param extras The notification's extras Bundle
     * @param packageName The package name for logging
     * @return Intent URI string if extractable, null otherwise
     */
    fun tryExtractIntentUri(extras: Bundle?, packageName: String): String? {
        if (extras == null) return null

        try {
            // Try common extras that apps use for deep-linking
            // Check for explicit intent extra
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelable("android.intent.extra.INTENT", Intent::class.java)?.let { intent ->
                    val uri = intent.toUri(Intent.URI_INTENT_SCHEME)
                    Log.d(TAG, "Extracted intent URI from INTENT extra for $packageName")
                    return uri
                }
            } else {
                @Suppress("DEPRECATION")
                (extras.getParcelable<Intent>("android.intent.extra.INTENT"))?.let { intent ->
                    val uri = intent.toUri(Intent.URI_INTENT_SCHEME)
                    Log.d(TAG, "Extracted intent URI from INTENT extra for $packageName")
                    return uri
                }
            }

            // Check for URI data in common app-specific extras
            val uriKeys = listOf(
                "click_action", "gcm.notification.click_action",  // Firebase
                "deep_link", "deeplink", "url", "uri",           // Common patterns
                "android.support.customtabs.extra.SESSION"        // Custom tabs
            )

            for (key in uriKeys) {
                val value = extras.getString(key)
                if (!value.isNullOrBlank() && (value.startsWith("http") || value.contains("://"))) {
                    Log.d(TAG, "Extracted URI from '$key' extra for $packageName: $value")
                    return "intent:$value#Intent;end"
                }
            }

            Log.d(TAG, "No extractable intent URI found for $packageName")
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting intent URI for $packageName: ${e.message}")
        }

        return null
    }
}
