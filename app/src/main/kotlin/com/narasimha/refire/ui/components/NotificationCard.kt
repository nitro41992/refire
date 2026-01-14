package com.narasimha.refire.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.narasimha.refire.R
import com.narasimha.refire.data.model.NotificationInfo

/**
 * Card displaying a notification with swipe actions.
 * - Live cards: swipe right to dismiss, swipe left to snooze
 * - Dismissed cards: swipe left to snooze only
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCard(
    notification: NotificationInfo,
    onSnooze: (NotificationInfo) -> Unit,
    onDismiss: ((NotificationInfo) -> Unit)? = null,
    isDismissed: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberNoVelocitySwipeToDismissState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Swipe right = dismiss (only for Live cards)
                    if (!isDismissed && onDismiss != null) {
                        onDismiss(notification)  // Call immediately, delay handled in HomeScreen
                        true  // Let card slide off screen
                    } else {
                        false
                    }
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    // Swipe left = snooze (for both)
                    onSnooze(notification)
                    false  // Don't dismiss card, just trigger action
                }
                else -> false
            }
        }
    )

    NoVelocitySwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeBackground(dismissState, isDismissed) },
        modifier = modifier,
        enableDismissFromStartToEnd = !isDismissed && onDismiss != null,
        enableDismissFromEndToStart = true
    ) {
        BaseNotificationCard(
            packageName = notification.packageName,
            title = notification.title ?: stringResource(R.string.notification_no_title),
            appName = notification.appName,
            messages = notification.messages,
            fallbackText = notification.getBestTextContent(),
            metadata = null
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(
    dismissState: NoVelocitySwipeToDismissState,
    isDismissed: Boolean
) {
    val direction = dismissState.swipeDirection

    val color = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> {
            if (!isDismissed) MaterialTheme.colorScheme.errorContainer
            else Color.Transparent
        }
        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }

    val alignment = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
        else -> Alignment.Center
    }

    val icon = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> if (!isDismissed) Icons.Default.Archive else null
        SwipeToDismissBoxValue.EndToStart -> Icons.Default.NotificationsOff
        else -> null
    }

    val iconTint = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.onErrorContainer
        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> Color.Transparent
    }

    val label = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> if (!isDismissed) stringResource(R.string.action_dismiss) else null
        SwipeToDismissBoxValue.EndToStart -> stringResource(R.string.action_snooze)
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .padding(horizontal = 20.dp),
        contentAlignment = alignment
    ) {
        if (icon != null && label != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = label,
                        color = iconTint,
                        style = MaterialTheme.typography.labelLarge
                    )
                } else {
                    Text(
                        text = label,
                        color = iconTint,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
