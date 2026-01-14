package com.narasimha.refire.ui.components

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.narasimha.refire.data.model.MessageData

/**
 * Unified card component for displaying notifications and snooze records.
 *
 * @param packageName Package name for app icon
 * @param title Main title text
 * @param appName App name subtitle
 * @param messages List of messages (for grouped notifications)
 * @param fallbackText Text to show if messages is empty
 * @param metadata Composable slot for metadata pills (source, time, domain badge, etc.)
 * @param actions Composable slot for action buttons (snooze, extend, cancel, etc.)
 * @param onClick Optional click handler for the entire card (typically for launching app/URL)
 * @param modifier Modifier for the card
 */
@Composable
fun BaseNotificationCard(
    packageName: String,
    title: String,
    appName: String,
    messages: List<MessageData> = emptyList(),
    fallbackText: String? = null,
    metadata: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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
            // App icon
            val context = LocalContext.current
            val appIcon = remember(packageName) {
                try {
                    val drawable = context.packageManager.getApplicationIcon(packageName)
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
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Content column
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Messages or fallback text
                if (messages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        messages.take(5).forEach { message ->
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (messages.size > 5) {
                            Text(
                                text = "+${messages.size - 5} more",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontStyle = FontStyle.Italic
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    fallbackText?.takeIf { it.isNotBlank() }?.let { text ->
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

                // Metadata pills (optional)
                metadata?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    it()
                }

                // App name
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = appName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Actions (passed as composable slot)
            actions?.let {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    it()
                }
            }
        }
    }
}
