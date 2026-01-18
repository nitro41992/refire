package com.narasimha.refire

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.util.Log
import com.narasimha.refire.service.ReFireNotificationListener

class ReFire : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Room database
        com.narasimha.refire.data.database.ReFireDatabase.getInstance(applicationContext)

        // Create notification channels
        createReFireNotificationChannel()
        createHelperNotificationChannel()

        // Check for app update and rebind NotificationListenerService if needed
        rebindNotificationListenerIfUpdated()
    }

    /**
     * Create notification channel for re-fire notifications at app startup.
     * This ensures the channel exists before any re-fire notifications are posted.
     */
    private fun createReFireNotificationChannel() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.refire_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.refire_channel_description)
        }

        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Create low-priority notification channel for the helper notification.
     * This channel is silent and used for the persistent "X notifications active" helper.
     */
    private fun createHelperNotificationChannel() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            HELPER_CHANNEL_ID,
            getString(R.string.helper_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.helper_channel_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }

        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Detect app version changes and request rebind of NotificationListenerService.
     * After app updates, the NotificationListenerService disconnects and normally requires
     * users to manually toggle Notification Access off/on. This method automatically
     * requests a rebind when the app version changes.
     */
    private fun rebindNotificationListenerIfUpdated() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val lastVersionCode = prefs.getInt("last_version_code", -1)
        val currentVersionCode = BuildConfig.VERSION_CODE

        if (lastVersionCode != currentVersionCode) {
            Log.i(TAG, "App version changed: $lastVersionCode -> $currentVersionCode, requesting rebind")

            val componentName = ComponentName(this, ReFireNotificationListener::class.java)
            NotificationListenerService.requestRebind(componentName)

            prefs.edit().putInt("last_version_code", currentVersionCode).apply()
        }
    }

    companion object {
        private const val TAG = "ReFire"
        const val CHANNEL_ID = "refire_channel"
        const val HELPER_CHANNEL_ID = "helper_channel"
    }
}
