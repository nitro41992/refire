package com.narasimha.refire.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Handles action button clicks from the notification helper.
 */
class HelperActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received action: ${intent.action}")

        when (intent.action) {
            ACTION_DISMISS_ALL -> {
                // Dismiss all notifications immediately
                ReFireNotificationListener.dismissAllNotifications()
                Log.i(TAG, "Dismissed all notifications via helper")
            }
            // ACTION_CLEAR_SCHEDULE is now handled directly by MainActivity via PendingIntent.getActivity
        }
    }

    companion object {
        private const val TAG = "HelperActionReceiver"
        const val ACTION_DISMISS_ALL = "com.narasimha.refire.ACTION_HELPER_DISMISS_ALL"
    }
}
