package com.narasimha.refire.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SharedPreferences wrapper for data retention settings.
 * Provides observable state flows for reactive UI updates.
 */
class RetentionPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _dismissedRetentionHours = MutableStateFlow(prefs.getInt(KEY_DISMISSED_RETENTION, DEFAULT_DISMISSED_RETENTION))
    val dismissedRetentionHours: StateFlow<Int> = _dismissedRetentionHours.asStateFlow()

    private val _historyRetentionDays = MutableStateFlow(prefs.getInt(KEY_HISTORY_RETENTION, DEFAULT_HISTORY_RETENTION))
    val historyRetentionDays: StateFlow<Int> = _historyRetentionDays.asStateFlow()

    fun setDismissedRetentionHours(hours: Int) {
        val clamped = hours.coerceIn(MIN_DISMISSED_RETENTION, MAX_DISMISSED_RETENTION)
        prefs.edit().putInt(KEY_DISMISSED_RETENTION, clamped).apply()
        _dismissedRetentionHours.value = clamped
    }

    fun setHistoryRetentionDays(days: Int) {
        val clamped = days.coerceIn(MIN_HISTORY_RETENTION, MAX_HISTORY_RETENTION)
        prefs.edit().putInt(KEY_HISTORY_RETENTION, clamped).apply()
        _historyRetentionDays.value = clamped
    }

    companion object {
        private const val PREFS_NAME = "retention_preferences"
        private const val KEY_DISMISSED_RETENTION = "dismissed_retention_hours"
        private const val KEY_HISTORY_RETENTION = "history_retention_days"

        const val DEFAULT_DISMISSED_RETENTION = 4
        const val MIN_DISMISSED_RETENTION = 1
        const val MAX_DISMISSED_RETENTION = 24

        const val DEFAULT_HISTORY_RETENTION = 1
        const val MIN_HISTORY_RETENTION = 1
        const val MAX_HISTORY_RETENTION = 7

        @Volatile
        private var instance: RetentionPreferences? = null

        fun getInstance(context: Context): RetentionPreferences {
            return instance ?: synchronized(this) {
                instance ?: RetentionPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}
