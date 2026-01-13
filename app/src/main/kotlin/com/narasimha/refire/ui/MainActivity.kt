package com.narasimha.refire.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.narasimha.refire.service.ReFireNotificationListener
import com.narasimha.refire.ui.screens.HomeScreen
import com.narasimha.refire.ui.screens.PermissionScreen
import com.narasimha.refire.ui.theme.ReFireTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ReFireTheme {
                MainContent()
            }
        }
    }
}

@Composable
private fun MainContent() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasNotificationAccess by remember { mutableStateOf(false) }
    var hasPostNotificationsPermission by remember { mutableStateOf(true) } // Default true for Android <13

    // Permission launcher for POST_NOTIFICATIONS (Android 13+)
    val postNotificationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPostNotificationsPermission = isGranted
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            hasNotificationAccess = isNotificationListenerEnabled(context)
            hasPostNotificationsPermission = checkPostNotificationsPermission(context)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (hasNotificationAccess && hasPostNotificationsPermission) {
            HomeScreen()
        } else {
            PermissionScreen(
                hasNotificationAccess = hasNotificationAccess,
                hasPostNotificationsPermission = hasPostNotificationsPermission,
                requiresPostNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                onRequestNotificationAccess = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    context.startActivity(intent)
                },
                onRequestPostNotifications = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
        }
    }
}

private fun isNotificationListenerEnabled(context: android.content.Context): Boolean {
    val componentName = ComponentName(context, ReFireNotificationListener::class.java)
    val enabledListeners = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    )
    return enabledListeners?.contains(componentName.flattenToString()) == true
}

private fun checkPostNotificationsPermission(context: android.content.Context): Boolean {
    // POST_NOTIFICATIONS only required on Android 13+
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return true
    }

    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}
