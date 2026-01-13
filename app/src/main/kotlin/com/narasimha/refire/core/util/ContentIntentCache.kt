package com.narasimha.refire.core.util

import android.app.PendingIntent
import android.util.Log

/**
 * In-memory cache for notification contentIntents.
 * Used for jump-back navigation when snoozes expire.
 *
 * Note: This cache is cleared on app restart. Snoozes that span
 * app restarts will fall back to launcher intent.
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
}
