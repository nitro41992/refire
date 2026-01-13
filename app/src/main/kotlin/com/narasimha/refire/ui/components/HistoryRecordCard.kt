package com.narasimha.refire.ui.components

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.narasimha.refire.R
import com.narasimha.refire.data.model.SnoozeRecord
import com.narasimha.refire.data.model.SnoozeSource

/**
 * Card displaying a history (expired) snooze with swipe actions.
 * - Swipe right: Delete from history
 * - Swipe left: Re-snooze
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryRecordCard(
    record: SnoozeRecord,
    onReSnooze: (SnoozeRecord) -> Unit,
    onDelete: (SnoozeRecord) -> Unit,
    onOpen: ((SnoozeRecord) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val deleteLabel = stringResource(R.string.action_delete)
    val reSnoozeLabel = stringResource(R.string.action_resnooze)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onDelete(record)
                    true
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onReSnooze(record)
                    false
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            SwipeBackground(
                dismissState = dismissState,
                deleteLabel = deleteLabel,
                reSnoozeLabel = reSnoozeLabel
            )
        },
        modifier = modifier,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true
    ) {
        HistoryCardContent(
            record = record,
            onOpen = onOpen
        )
    }
}

@Composable
private fun HistoryCardContent(
    record: SnoozeRecord,
    onOpen: ((SnoozeRecord) -> Unit)?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onOpen != null) Modifier.clickable { onOpen(record) } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header row with icon and title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App icon
                val context = LocalContext.current
                val appIcon = remember(record.packageName) {
                    try {
                        val drawable = context.packageManager.getApplicationIcon(record.packageName)
                        (drawable as? BitmapDrawable)?.bitmap?.asImageBitmap()
                            ?: drawable.toBitmap().asImageBitmap()
                    } catch (e: Exception) {
                        null
                    }
                }

                if (appIcon != null) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = record.appName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Messages or fallback text
            if (record.messages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    record.messages.take(3).forEach { message ->
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (record.messages.size > 3) {
                        Text(
                            text = "+${record.messages.size - 3} more",
                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                if (record.isSharedUrl()) {
                    record.getDomain()?.let { domain ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = domain,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                record.getDisplayText()?.takeIf { it.isNotBlank() }?.let { text ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = if (record.isSharedUrl()) FontFamily.Monospace else FontFamily.Default
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom row with source badge, re-fired time, and action pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Source badge and re-fired time
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { },
                        label = {
                            Text(
                                text = when (record.source) {
                                    SnoozeSource.NOTIFICATION -> stringResource(R.string.source_notification)
                                    SnoozeSource.SHARE_SHEET -> stringResource(R.string.source_shared)
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = when (record.source) {
                                    SnoozeSource.NOTIFICATION -> Icons.Default.NotificationsOff
                                    SnoozeSource.SHARE_SHEET -> Icons.Default.Link
                                },
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.height(28.dp)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = record.formattedTimeSinceRefired(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Action pills
                SwipeHintPills()
            }
        }
    }
}

@Composable
private fun SwipeHintPills(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Delete pill
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.action_delete),
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .size(16.dp)
            )
        }
        // Re-snooze pill
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                imageVector = Icons.Default.Snooze,
                contentDescription = stringResource(R.string.action_resnooze),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .size(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(
    dismissState: SwipeToDismissBoxState,
    deleteLabel: String,
    reSnoozeLabel: String
) {
    val direction = dismissState.dismissDirection

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
        SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Close
        SwipeToDismissBoxValue.EndToStart -> Icons.Default.Snooze
        else -> null
    }

    val iconTint = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.onErrorContainer
        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> Color.Transparent
    }

    val label = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> deleteLabel
        SwipeToDismissBoxValue.EndToStart -> reSnoozeLabel
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
