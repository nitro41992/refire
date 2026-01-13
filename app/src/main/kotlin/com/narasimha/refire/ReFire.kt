package com.narasimha.refire

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

class ReFire : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Room database
        com.narasimha.refire.data.database.ReFireDatabase.getInstance(applicationContext)

        // Create notification channel for re-fire notifications
        createReFireNotificationChannel()
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

    companion object {
        const val CHANNEL_ID = "refire_channel"
    }
}
