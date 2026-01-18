package com.narasimha.refire.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.narasimha.refire.R
import com.narasimha.refire.data.model.IgnoreScope

/**
 * Dialog for confirming thread ignore action with scope choice.
 * Always offers both options: ignore thread or ignore all from app.
 * Default selection based on isConversation:
 * - Conversation apps (WhatsApp, SMS) → Default to THREAD
 * - Non-conversation apps (Blip) → Default to APP
 */
@Composable
fun IgnoreConfirmationDialog(
    displayTitle: String,
    appName: String,
    isConversation: Boolean,
    onConfirm: (IgnoreScope) -> Unit,
    onDismiss: () -> Unit
) {
    // Default based on whether it's a conversation app
    var selectedScope by remember { mutableStateOf(if (isConversation) IgnoreScope.THREAD else IgnoreScope.APP) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.ignore_dialog_title))
        },
        text = {
            Column {
                // Thread option
                IgnoreOptionRow(
                    selected = selectedScope == IgnoreScope.THREAD,
                    onClick = { selectedScope = IgnoreScope.THREAD },
                    label = stringResource(R.string.ignore_option_thread, displayTitle),
                    description = stringResource(R.string.ignore_option_thread_desc)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // App option
                IgnoreOptionRow(
                    selected = selectedScope == IgnoreScope.APP,
                    onClick = { selectedScope = IgnoreScope.APP },
                    label = stringResource(R.string.ignore_option_app, appName),
                    description = stringResource(R.string.ignore_option_app_desc)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(selectedScope)
                onDismiss()
            }) {
                Text(stringResource(R.string.ignore_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun IgnoreOptionRow(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Dialog for confirming thread unignore action.
 */
@Composable
fun UnignoreConfirmationDialog(
    displayTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.unignore_dialog_title))
        },
        text = {
            Text(
                text = stringResource(R.string.unignore_dialog_description, displayTitle),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text(stringResource(R.string.action_unignore))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
