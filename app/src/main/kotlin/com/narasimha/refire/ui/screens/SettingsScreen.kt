package com.narasimha.refire.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.narasimha.refire.R
import com.narasimha.refire.data.preferences.HelperPreferences
import com.narasimha.refire.data.preferences.RetentionPreferences
import com.narasimha.refire.service.ReFireNotificationListener
import com.narasimha.refire.ui.theme.AppNameTextStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val helperPreferences = HelperPreferences.getInstance(context)
    val isHelperEnabled by helperPreferences.isHelperEnabled.collectAsState()
    val helperThreshold by helperPreferences.helperThreshold.collectAsState()

    val retentionPreferences = RetentionPreferences.getInstance(context)
    val dismissedRetentionHours by retentionPreferences.dismissedRetentionHours.collectAsState()
    val historyRetentionDays by retentionPreferences.historyRetentionDays.collectAsState()

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

            Spacer(modifier = Modifier.height(32.dp))
        }
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
