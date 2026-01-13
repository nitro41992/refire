package com.narasimha.refire.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.narasimha.refire.R

/**
 * Onboarding screen explaining why permissions are needed.
 * Shows separate cards for Notification Access and POST_NOTIFICATIONS.
 */
@Composable
fun PermissionScreen(
    hasNotificationAccess: Boolean,
    hasPostNotificationsPermission: Boolean,
    requiresPostNotifications: Boolean,
    onRequestNotificationAccess: () -> Unit,
    onRequestPostNotifications: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.permission_description),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Notification Access Permission Card
        PermissionCard(
            title = stringResource(R.string.permission_notification_access_title),
            description = stringResource(R.string.permission_notification_access_description),
            isGranted = hasNotificationAccess,
            buttonText = stringResource(R.string.permission_notification_access_button),
            onRequest = onRequestNotificationAccess
        )

        // POST_NOTIFICATIONS Permission Card (Android 13+ only)
        if (requiresPostNotifications) {
            Spacer(modifier = Modifier.height(16.dp))

            PermissionCard(
                title = stringResource(R.string.permission_post_notifications_title),
                description = stringResource(R.string.permission_post_notifications_description),
                isGranted = hasPostNotificationsPermission,
                buttonText = stringResource(R.string.permission_post_notifications_button),
                onRequest = onRequestPostNotifications
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Privacy assurances
        PrivacyAssurance(
            icon = Icons.Default.Security,
            text = stringResource(R.string.privacy_local)
        )

        Spacer(modifier = Modifier.height(8.dp))

        PrivacyAssurance(
            icon = Icons.Default.Security,
            text = stringResource(R.string.privacy_deleted)
        )

        Spacer(modifier = Modifier.height(8.dp))

        PrivacyAssurance(
            icon = Icons.Default.Security,
            text = stringResource(R.string.privacy_no_server)
        )
    }
}

/**
 * Card for a single permission with grant status and request button.
 */
@Composable
private fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    buttonText: String,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                if (isGranted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Granted",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isGranted) {
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onRequest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = buttonText)
                }
            }
        }
    }
}

@Composable
private fun PrivacyAssurance(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.tertiary
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
