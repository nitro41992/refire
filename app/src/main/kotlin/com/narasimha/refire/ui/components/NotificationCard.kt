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
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
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
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Swipe right = dismiss (only for Live cards)
                    if (!isDismissed && onDismiss != null) {
                        onDismiss(notification)
                        true
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

    SwipeToDismissBox(
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
            metadata = null,
            actions = {
                SwipeHintPills(
                    showDismiss = !isDismissed && onDismiss != null,
                    onDismissClick = { onDismiss?.invoke(notification) },
                    onSnoozeClick = { onSnooze(notification) }
                )
            }
        )
    }
}

/**
 * Clickable icon-only pills showing available actions.
 */
@Composable
private fun SwipeHintPills(
    showDismiss: Boolean,
    onDismissClick: () -> Unit,
    onSnoozeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showDismiss) {
            // Dismiss pill
            Surface(
                onClick = onDismissClick,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Icon(
                    imageVector = Icons.Default.SwipeLeft,
                    contentDescription = stringResource(R.string.action_dismiss),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .size(20.dp)
                )
            }
        }
        // Snooze pill
        Surface(
            onClick = onSnoozeClick,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsOff,
                contentDescription = stringResource(R.string.action_snooze),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(
    dismissState: SwipeToDismissBoxState,
    isDismissed: Boolean
) {
    val direction = dismissState.dismissDirection

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
        SwipeToDismissBoxValue.StartToEnd -> if (!isDismissed) Icons.Default.SwipeLeft else null
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
