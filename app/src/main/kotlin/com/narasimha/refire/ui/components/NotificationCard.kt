package com.narasimha.refire.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.narasimha.refire.R
import com.narasimha.refire.core.util.AppIconCache
import com.narasimha.refire.data.model.NotificationInfo

/**
 * Card displaying a notification with swipe actions.
 * - Live cards: swipe right to dismiss, swipe left to snooze
 * - Dismissed cards: swipe left to snooze only
 * - Long-press: trigger ignore action
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotificationCard(
    notification: NotificationInfo,
    onSnooze: (NotificationInfo) -> Unit,
    onDismiss: ((NotificationInfo) -> Unit)? = null,
    onClick: ((NotificationInfo) -> Unit)? = null,
    onLongPress: ((NotificationInfo) -> Unit)? = null,
    isDismissed: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Track pending dismiss to defer callback until animation completes
    val pendingDismiss = remember { mutableStateOf(false) }

    val dismissState = rememberNoVelocitySwipeToDismissState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Swipe right = dismiss (only for Live cards)
                    if (!isDismissed && onDismiss != null) {
                        pendingDismiss.value = true
                        true  // Let card slide off screen (animation will complete)
                    } else {
                        false
                    }
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    // Swipe left = snooze
                    onSnooze(notification)
                    false  // Don't dismiss card, just trigger action
                }
                SwipeToDismissBoxValue.Settled -> true
                else -> false
            }
        }
    )

    // Trigger dismiss callback after animation settles to the dismissed state
    LaunchedEffect(dismissState.currentValue, pendingDismiss.value) {
        if (pendingDismiss.value && dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd) {
            onDismiss?.invoke(notification)
            pendingDismiss.value = false
        }
    }

    NoVelocitySwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeBackground(dismissState, isDismissed) },
        modifier = modifier,
        enableDismissFromStartToEnd = !isDismissed && onDismiss != null,
        enableDismissFromEndToStart = true
    ) {
        NotificationCardContent(
            notification = notification,
            showTimestamp = true,
            modifier = Modifier.combinedClickable(
                onClick = { onClick?.invoke(notification) },
                onLongClick = { onLongPress?.invoke(notification) }
            )
        )
    }
}

@Composable
private fun NotificationCardContent(
    notification: NotificationInfo,
    showTimestamp: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // App icon (cached via AppIconCache)
            val context = LocalContext.current
            val appIcon = remember(notification.packageName) {
                AppIconCache.getAppIcon(context, notification.packageName)
            }

            if (appIcon != null) {
                Image(
                    bitmap = appIcon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title ?: "Notification",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = notification.appName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Messages
                if (notification.messages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        notification.messages.take(5).forEach { message ->
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (notification.messages.size > 5) {
                            Text(
                                text = "+${notification.messages.size - 5} more",
                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    // Fallback text
                    notification.getBestTextContent().takeIf { it.isNotBlank() }?.let { text ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Type indicator icon + timestamp (top-right)
            if (showTimestamp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = stringResource(R.string.type_active_notification),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    val formattedTime = remember(notification.postTime) {
                        notification.formattedTimeSincePosted()
                    }
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
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
