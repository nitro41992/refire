package com.narasimha.refire.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    BaseNotificationCard(
        packageName = notification.packageName,
        title = notification.title ?: stringResource(R.string.notification_no_title),
        appName = notification.appName,
        messages = notification.messages,
        fallbackText = notification.getBestTextContent(),
        isDismissed = isDismissed,
        metadata = null,  // No metadata pills for active notifications
        actions = {
            FilledTonalButton(
                onClick = { onSnooze(notification) }
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
        },
        modifier = modifier
    )
}
