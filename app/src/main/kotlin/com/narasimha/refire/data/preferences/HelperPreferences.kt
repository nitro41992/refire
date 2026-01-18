package com.narasimha.refire.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SharedPreferences wrapper for notification helper settings.
 * Provides observable state flows for reactive UI updates.
 */
class HelperPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isHelperEnabled = MutableStateFlow(prefs.getBoolean(KEY_HELPER_ENABLED, DEFAULT_ENABLED))
    val isHelperEnabled: StateFlow<Boolean> = _isHelperEnabled.asStateFlow()

    private val _helperThreshold = MutableStateFlow(prefs.getInt(KEY_HELPER_THRESHOLD, DEFAULT_THRESHOLD))
    val helperThreshold: StateFlow<Int> = _helperThreshold.asStateFlow()

    fun setHelperEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HELPER_ENABLED, enabled).apply()
        _isHelperEnabled.value = enabled
    }

    fun setHelperThreshold(threshold: Int) {
        val clamped = threshold.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
        prefs.edit().putInt(KEY_HELPER_THRESHOLD, clamped).apply()
        _helperThreshold.value = clamped
    }

    companion object {
        private const val PREFS_NAME = "helper_preferences"
        private const val KEY_HELPER_ENABLED = "helper_enabled"
        private const val KEY_HELPER_THRESHOLD = "helper_threshold"

        const val DEFAULT_ENABLED = true
        const val DEFAULT_THRESHOLD = 3
        const val MIN_THRESHOLD = 1
        const val MAX_THRESHOLD = 10

        @Volatile
        private var instance: HelperPreferences? = null

        fun getInstance(context: Context): HelperPreferences {
            return instance ?: synchronized(this) {
                instance ?: HelperPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}
