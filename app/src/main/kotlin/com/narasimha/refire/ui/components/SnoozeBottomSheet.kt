package com.narasimha.refire.ui.components

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.narasimha.refire.core.util.TimeExpressionParser
import com.narasimha.refire.data.model.NotificationInfo
import com.narasimha.refire.data.model.SharedContent
import com.narasimha.refire.data.model.SnoozePreset
import com.narasimha.refire.data.model.SnoozeRecord

@Composable
fun SnoozeBottomSheet(
    sharedContent: SharedContent? = null,
    notification: NotificationInfo? = null,
    snoozeRecord: SnoozeRecord? = null,
    isLoading: Boolean = false,
    onSnoozeSelected: (SnoozePreset) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current

    var timeInput by remember { mutableStateOf("") }
    val parseResult = remember(timeInput) {
        TimeExpressionParser.parse(timeInput)
    }

    // Get the title and package name to display
    val displayTitle = notification?.title
        ?: snoozeRecord?.title
        ?: sharedContent?.getDisplayTitle()
        ?: "Schedule"

    val packageName = notification?.packageName
        ?: snoozeRecord?.packageName
        ?: sharedContent?.sourcePackage

    // Get app icon
    val appIcon = remember(packageName) {
        packageName?.let { pkg ->
            try {
                val drawable = context.packageManager.getApplicationIcon(pkg)
                (drawable as? BitmapDrawable)?.bitmap?.asImageBitmap()
                    ?: drawable.toBitmap().asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    // Submit function
    val submitSchedule: (SnoozePreset) -> Unit = { preset ->
        keyboardController?.hide()
        onSnoozeSelected(preset)
    }

    // Dismiss function
    val dismissSheet: () -> Unit = {
        keyboardController?.hide()
        onDismiss()
    }

    KeyboardAnimatingBottomSheet(
        visible = true,
        onDismiss = dismissSheet,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header: App icon + Title + Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App icon
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = dismissSheet) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Call to action
            Text(
                text = "If not now, when?",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Time input field
            OutlinedTextField(
                value = timeInput,
                onValueChange = { timeInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.headlineMedium,
                placeholder = {
                    Text(
                        text = "at 7pm, in 2h, 30m...",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (parseResult.isValid) {
                            parseResult.preset?.let { submitSchedule(it) }
                        }
                    }
                )
            )

            // Feedback row - shows validation result only when user has typed something
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 8.dp, start = 4.dp)
                    .height(16.dp) // Fixed height for layout stability
            ) {
                if (timeInput.isNotEmpty() && parseResult.isValid && parseResult.preset != null) {
                    // Valid input - green confirmation
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = parseResult.displayText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                // Empty input or invalid: show nothing (placeholder is sufficient guidance)
            }

            // Preset chips - 3x2 grid with centered text
            val presets = SnoozePreset.defaults()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // First row: 5m, 30m, 1h
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.take(3).forEach { preset ->
                        AssistChip(
                            onClick = { submitSchedule(preset) },
                            modifier = Modifier.weight(1f),
                            label = {
                                Text(
                                    text = preset.displayLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp)
                                )
                            }
                        )
                    }
                }
                // Second row: 3h, 12h, 24h
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.drop(3).forEach { preset ->
                        AssistChip(
                            onClick = { submitSchedule(preset) },
                            modifier = Modifier.weight(1f),
                            label = {
                                Text(
                                    text = preset.displayLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    // Request focus immediately to open keyboard with the sheet
    LaunchedEffect(Unit) {
        // Small delay to ensure the sheet is visible before requesting focus
        kotlinx.coroutines.delay(100)
        focusRequester.requestFocus()
    }
}
