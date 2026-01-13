package com.narasimha.refire.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.narasimha.refire.R
import com.narasimha.refire.data.model.NotificationInfo

/**
 * Card displaying a notification with snooze action.
 */
@Composable
fun NotificationCard(
    notification: NotificationInfo,
    onSnooze: (NotificationInfo) -> Unit,
    isDismissed: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cardAlpha = if (isDismissed) 0.7f else 1f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isDismissed) Modifier.alpha(cardAlpha) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Notification icon
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Title
                Text(
                    text = notification.title ?: stringResource(R.string.notification_no_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Text content
                notification.getBestTextContent().takeIf { it.isNotBlank() }?.let { text ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Package name
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatPackageName(notification.packageName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Snooze button
            FilledTonalButton(
                onClick = { onSnooze(notification) },
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsOff,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.action_snooze),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/**
 * Format package name for display.
 * Extracts the app name from package (e.g., "com.whatsapp" -> "WhatsApp")
 */
private fun formatPackageName(packageName: String): String {
    val knownApps = mapOf(
        "com.whatsapp" to "WhatsApp",
        "com.facebook.orca" to "Messenger",
        "org.telegram.messenger" to "Telegram",
        "com.discord" to "Discord",
        "com.slack" to "Slack",
        "com.google.android.apps.messaging" to "Messages",
        "com.google.android.gm" to "Gmail",
        "com.microsoft.office.outlook" to "Outlook"
    )

    return knownApps[packageName] ?: packageName.substringAfterLast(".")
        .replaceFirstChar { it.uppercase() }
}
