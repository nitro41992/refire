package com.narasimha.refire.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.narasimha.refire.R
import com.narasimha.refire.data.database.ReFireDatabase
import com.narasimha.refire.data.preferences.HelperPreferences
import com.narasimha.refire.data.preferences.RetentionPreferences
import com.narasimha.refire.data.repository.SnoozeRepository
import com.narasimha.refire.service.ReFireNotificationListener
import com.narasimha.refire.ui.theme.AppNameTextStyle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val helperPreferences = HelperPreferences.getInstance(context)
    val isHelperEnabled by helperPreferences.isHelperEnabled.collectAsState()
    val helperThreshold by helperPreferences.helperThreshold.collectAsState()

    val retentionPreferences = RetentionPreferences.getInstance(context)
    val dismissedRetentionHours by retentionPreferences.dismissedRetentionHours.collectAsState()
    val historyRetentionDays by retentionPreferences.historyRetentionDays.collectAsState()

    // Ignored threads navigation
    var showIgnoredScreen by remember { mutableStateOf(false) }
    val ignoredCount by ReFireNotificationListener.ignoredCount.collectAsState(initial = 0)

    // Repository and counts for clear actions
    val repository = remember {
        val database = ReFireDatabase.getInstance(context)
        SnoozeRepository(database.snoozeDao())
    }
    val dismissedRecords by ReFireNotificationListener.dismissedRecords.collectAsState()
    val historyRecords by ReFireNotificationListener.historySnoozes.collectAsState()
    val dismissedCount = dismissedRecords.size
    val historyCount = historyRecords.size

    // Confirmation dialog state
    var showClearDismissedDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = AppNameTextStyle.copy(color = Color.White)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Notification Helper Section
            SettingsSectionHeader(
                title = stringResource(R.string.settings_section_helper)
            )

            // Enable/Disable Toggle
            SettingsToggleItem(
                title = stringResource(R.string.settings_helper_toggle_title),
                description = stringResource(R.string.settings_helper_toggle_description),
                checked = isHelperEnabled,
                onCheckedChange = { enabled ->
                    helperPreferences.setHelperEnabled(enabled)
                    // Immediately update the helper notification based on new setting
                    ReFireNotificationListener.updateHelperNotification()
                }
            )

            // Threshold Slider
            SettingsSliderItem(
                title = stringResource(R.string.settings_helper_threshold_title),
                description = stringResource(R.string.settings_helper_threshold_description, helperThreshold),
                value = helperThreshold.toFloat(),
                valueRange = HelperPreferences.MIN_THRESHOLD.toFloat()..HelperPreferences.MAX_THRESHOLD.toFloat(),
                steps = HelperPreferences.MAX_THRESHOLD - HelperPreferences.MIN_THRESHOLD - 1,
                enabled = isHelperEnabled,
                onValueChange = { value ->
                    helperPreferences.setHelperThreshold(value.roundToInt())
                    // Immediately update the helper notification based on new threshold
                    ReFireNotificationListener.updateHelperNotification()
                }
            )

            // Data Retention Section
            SettingsSectionHeader(
                title = stringResource(R.string.settings_section_retention)
            )

            // Dismissed items retention
            SettingsSliderItem(
                title = stringResource(R.string.settings_dismissed_retention_title),
                description = stringResource(R.string.settings_dismissed_retention_description, dismissedRetentionHours),
                value = dismissedRetentionHours.toFloat(),
                valueRange = RetentionPreferences.MIN_DISMISSED_RETENTION.toFloat()..RetentionPreferences.MAX_DISMISSED_RETENTION.toFloat(),
                steps = RetentionPreferences.MAX_DISMISSED_RETENTION - RetentionPreferences.MIN_DISMISSED_RETENTION - 1,
                enabled = true,
                onValueChange = { value ->
                    retentionPreferences.setDismissedRetentionHours(value.roundToInt())
                }
            )

            // Clear dismissed button
            SettingsActionItem(
                title = stringResource(R.string.settings_clear_dismissed),
                description = stringResource(R.string.settings_clear_dismissed_description),
                itemCount = dismissedCount,
                enabled = dismissedCount > 0,
                onClick = { showClearDismissedDialog = true }
            )

            // History retention
            SettingsSliderItem(
                title = stringResource(R.string.settings_history_retention_title),
                description = stringResource(R.string.settings_history_retention_description, historyRetentionDays),
                value = historyRetentionDays.toFloat(),
                valueRange = RetentionPreferences.MIN_HISTORY_RETENTION.toFloat()..RetentionPreferences.MAX_HISTORY_RETENTION.toFloat(),
                steps = RetentionPreferences.MAX_HISTORY_RETENTION - RetentionPreferences.MIN_HISTORY_RETENTION - 1,
                enabled = true,
                onValueChange = { value ->
                    retentionPreferences.setHistoryRetentionDays(value.roundToInt())
                }
            )

            // Clear history button
            SettingsActionItem(
                title = stringResource(R.string.settings_clear_history),
                description = stringResource(R.string.settings_clear_history_description),
                itemCount = historyCount,
                enabled = historyCount > 0,
                onClick = { showClearHistoryDialog = true }
            )

            // Ignored Threads Section
            SettingsSectionHeader(
                title = stringResource(R.string.settings_section_ignored)
            )

            SettingsNavigationItem(
                title = stringResource(R.string.settings_ignored_manage),
                description = stringResource(R.string.settings_ignored_manage_description),
                badge = if (ignoredCount > 0) ignoredCount else null,
                onClick = { showIgnoredScreen = true }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Ignored threads screen (full-screen overlay with slide animation)
    AnimatedVisibility(
        visible = showIgnoredScreen,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(durationMillis = 250)
        ),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(durationMillis = 200)
        )
    ) {
        IgnoredThreadsScreen(
            onBack = { showIgnoredScreen = false }
        )
    }

    // Clear dismissed confirmation dialog
    if (showClearDismissedDialog) {
        ClearConfirmationDialog(
            title = stringResource(R.string.settings_clear_confirm_title_dismissed),
            message = stringResource(R.string.settings_clear_confirm_message_dismissed, dismissedCount),
            onConfirm = {
                showClearDismissedDialog = false
                scope.launch {
                    val deleted = repository.clearAllDismissed()
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.settings_clear_success, deleted)
                    )
                }
            },
            onDismiss = { showClearDismissedDialog = false }
        )
    }

    // Clear history confirmation dialog
    if (showClearHistoryDialog) {
        ClearConfirmationDialog(
            title = stringResource(R.string.settings_clear_confirm_title_history),
            message = stringResource(R.string.settings_clear_confirm_message_history, historyCount),
            onConfirm = {
                showClearHistoryDialog = false
                scope.launch {
                    val deleted = repository.clearAllHistory()
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.settings_clear_success, deleted)
                    )
                }
            },
            onDismiss = { showClearHistoryDialog = false }
        )
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    )
}

@Composable
private fun SettingsToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsSliderItem(
    title: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val secondaryTextColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryTextColor
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = valueRange.start.toInt().toString(),
                style = MaterialTheme.typography.labelMedium,
                color = secondaryTextColor
            )
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            Text(
                text = valueRange.endInclusive.toInt().toString(),
                style = MaterialTheme.typography.labelMedium,
                color = secondaryTextColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsNavigationItem(
    title: String,
    description: String,
    badge: Int? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (badge != null) {
            Badge(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text("$badge")
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsActionItem(
    title: String,
    description: String,
    itemCount: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val secondaryTextColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryTextColor
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            if (itemCount > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) {
                    Text("$itemCount")
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(stringResource(R.string.settings_clear_confirm))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClearConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Warning icon header
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(16.dp)
                            .size(32.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Message
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.settings_clear_confirm))
                    }
                }
            }
        }
    }
}
