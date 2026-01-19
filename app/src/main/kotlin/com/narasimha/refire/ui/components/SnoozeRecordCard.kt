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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.narasimha.refire.R
import com.narasimha.refire.core.util.AppIconCache
import com.narasimha.refire.data.model.SnoozeRecord
import com.narasimha.refire.data.model.SnoozeSource

/**
 * Card displaying a snoozed item with swipe actions.
 * - Swipe right: Dismiss to history
 * - Swipe left: Extend snooze
 * - Long-press: trigger ignore action
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SnoozeRecordCard(
    snooze: SnoozeRecord,
    onDismiss: (SnoozeRecord) -> Unit,
    onExtend: (SnoozeRecord) -> Unit,
    onClick: ((SnoozeRecord) -> Unit)? = null,
    onLongPress: ((SnoozeRecord) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dismissLabel = stringResource(R.string.action_dismiss)
    val extendLabel = stringResource(R.string.action_extend)

    // Track pending dismiss to defer callback until animation completes
    val pendingDismiss = remember { mutableStateOf(false) }

    val dismissState = rememberNoVelocitySwipeToDismissState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    pendingDismiss.value = true
                    true  // Let card slide off screen (animation will complete)
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onExtend(snooze)
                    false
                }
                SwipeToDismissBoxValue.Settled -> true
                else -> false
            }
        }
    )

    // Trigger dismiss callback after animation settles to the dismissed state
    LaunchedEffect(dismissState.currentValue, pendingDismiss.value) {
        if (pendingDismiss.value && dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd) {
            onDismiss(snooze)
            pendingDismiss.value = false
        }
    }

    NoVelocitySwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            SwipeBackground(
                dismissState = dismissState,
                dismissLabel = dismissLabel,
                extendLabel = extendLabel
            )
        },
        modifier = modifier,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true
    ) {
        SnoozeCardContent(
            snooze = snooze,
            modifier = Modifier.combinedClickable(
                onClick = { onClick?.invoke(snooze) },
                onLongClick = { onLongPress?.invoke(snooze) }
            )
        )
    }
}

@Composable
private fun SnoozeCardContent(
    snooze: SnoozeRecord,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header row with icon, title, and metadata on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // App icon (cached via AppIconCache)
                val context = LocalContext.current
                val appIcon = remember(snooze.packageName) {
                    AppIconCache.getAppIcon(context, snooze.packageName)
                }

                if (appIcon != null) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = snooze.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        // Bright pill showing count of suppressed messages
                        if (snooze.suppressedCount > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "+${snooze.suppressedCount} new",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = snooze.appName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Source icon and time remaining (top-right)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = when (snooze.source) {
                            SnoozeSource.NOTIFICATION -> Icons.Default.Notifications
                            SnoozeSource.SHARE_SHEET -> Icons.Default.Link
                        },
                        contentDescription = when (snooze.source) {
                            SnoozeSource.NOTIFICATION -> stringResource(R.string.source_notification)
                            SnoozeSource.SHARE_SHEET -> stringResource(R.string.source_shared)
                        },
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    val formattedTime = remember(snooze.snoozeEndTime) {
                        snooze.formattedTimeRemaining()
                    }
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // For shared URLs: show domain + full URL
            if (snooze.isSharedUrl()) {
                snooze.getDomain()?.let { domain ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = domain,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Show the full URL to differentiate multiple links
                snooze.getDisplayText()?.takeIf { it.isNotBlank() }?.let { url ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else if (snooze.messages.isNotEmpty()) {
                // Messages for notifications
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    snooze.messages.take(5).forEach { message ->
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (snooze.messages.size > 5) {
                        Text(
                            text = "+${snooze.messages.size - 5} more",
                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                // Fallback text for non-URL items without messages
                snooze.getDisplayText()?.takeIf { it.isNotBlank() }?.let { text ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
    dismissLabel: String,
    extendLabel: String
) {
    val direction = dismissState.swipeDirection

    val color = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.errorContainer
        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }

    val alignment = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
        else -> Alignment.Center
    }

    val icon = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Archive
        SwipeToDismissBoxValue.EndToStart -> Icons.Default.MoreTime
        else -> null
    }

    val iconTint = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.onErrorContainer
        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> Color.Transparent
    }

    val label = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> dismissLabel
        SwipeToDismissBoxValue.EndToStart -> extendLabel
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
                    Text(text = label, color = iconTint, style = MaterialTheme.typography.labelLarge)
                } else {
                    Text(text = label, color = iconTint, style = MaterialTheme.typography.labelLarge)
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
