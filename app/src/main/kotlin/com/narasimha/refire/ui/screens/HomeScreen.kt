package com.narasimha.refire.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.narasimha.refire.R
import com.narasimha.refire.core.util.IntentUtils
import com.narasimha.refire.data.model.NotificationInfo
import com.narasimha.refire.data.model.SnoozePreset
import com.narasimha.refire.data.model.SnoozeRecord
import com.narasimha.refire.service.ReFireNotificationListener
import com.narasimha.refire.ui.components.HistoryRecordCard
import com.narasimha.refire.ui.components.NotificationCard
import com.narasimha.refire.ui.components.SnoozeBottomSheet
import com.narasimha.refire.ui.components.SnoozeRecordCard
import com.narasimha.refire.ui.util.groupNotificationsByThread
import com.narasimha.refire.ui.util.groupSnoozesByThread
import java.time.LocalDateTime
import android.content.Context
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private enum class FilterType {
    LIVE, DISMISSED, SNOOZED, HISTORY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activeNotifications by remember { mutableStateOf<List<NotificationInfo>>(emptyList()) }
    var recentlyDismissed by remember { mutableStateOf<List<NotificationInfo>>(emptyList()) }
    var snoozeRecords by remember { mutableStateOf<List<SnoozeRecord>>(emptyList()) }
    var historyRecords by remember { mutableStateOf<List<SnoozeRecord>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf(FilterType.LIVE) }

    // Bottom sheet state
    var showSnoozeSheet by remember { mutableStateOf(false) }
    var selectedNotification by remember { mutableStateOf<NotificationInfo?>(null) }
    var extendingSnooze by remember { mutableStateOf<SnoozeRecord?>(null) }
    var reSnoozeRecord by remember { mutableStateOf<SnoozeRecord?>(null) }

    // Snackbar state for undo functionality
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var deletedSnoozeRecord by remember { mutableStateOf<SnoozeRecord?>(null) }
    var isDeletedFromHistory by remember { mutableStateOf(false) }

    // Observe active notifications
    LaunchedEffect(Unit) {
        ReFireNotificationListener.activeNotifications.collectLatest { notifications ->
            activeNotifications = notifications
        }
    }

    // Observe recently dismissed notifications
    LaunchedEffect(Unit) {
        ReFireNotificationListener.recentsBuffer.collectLatest { recents ->
            recentlyDismissed = recents
        }
    }

    // Observe snooze records
    LaunchedEffect(Unit) {
        ReFireNotificationListener.snoozeRecords.collectLatest { records ->
            snoozeRecords = records.filter { !it.isExpired() }
        }
    }

    // Observe history records
    LaunchedEffect(Unit) {
        ReFireNotificationListener.historySnoozes.collectLatest { records ->
            historyRecords = records
        }
    }

    // Get string resources for snackbar (need to be outside coroutine)
    val snoozeCancelledText = stringResource(R.string.snackbar_snooze_cancelled)
    val historyDeletedText = stringResource(R.string.snackbar_history_deleted)
    val undoText = stringResource(R.string.snackbar_undo)

    // Apply grouping (outside Scaffold for bottom bar badge counts)
    val groupedActive = remember(activeNotifications) {
        activeNotifications.groupNotificationsByThread()
    }
    val groupedSnoozed = remember(snoozeRecords) {
        snoozeRecords.groupSnoozesByThread()
    }

    // Navigation item labels
    val liveLabel = stringResource(R.string.subtab_live)
    val dismissedLabel = stringResource(R.string.subtab_dismissed)
    val snoozedLabel = stringResource(R.string.subtab_snoozed)
    val historyLabel = stringResource(R.string.subtab_history)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedFilter == FilterType.LIVE,
                    onClick = { selectedFilter = FilterType.LIVE },
                    icon = {
                        BadgedBox(badge = {
                            if (groupedActive.isNotEmpty()) Badge { Text("${groupedActive.size}") }
                        }) {
                            Icon(Icons.Default.Notifications, contentDescription = liveLabel)
                        }
                    },
                    label = { Text(liveLabel) }
                )
                NavigationBarItem(
                    selected = selectedFilter == FilterType.DISMISSED,
                    onClick = { selectedFilter = FilterType.DISMISSED },
                    icon = {
                        BadgedBox(badge = {
                            if (recentlyDismissed.isNotEmpty()) Badge { Text("${recentlyDismissed.size}") }
                        }) {
                            Icon(Icons.Default.SwipeLeft, contentDescription = dismissedLabel)
                        }
                    },
                    label = { Text(dismissedLabel) }
                )
                NavigationBarItem(
                    selected = selectedFilter == FilterType.SNOOZED,
                    onClick = { selectedFilter = FilterType.SNOOZED },
                    icon = {
                        BadgedBox(badge = {
                            if (groupedSnoozed.isNotEmpty()) Badge { Text("${groupedSnoozed.size}") }
                        }) {
                            Icon(Icons.Default.NotificationsOff, contentDescription = snoozedLabel)
                        }
                    },
                    label = { Text(snoozedLabel) }
                )
                NavigationBarItem(
                    selected = selectedFilter == FilterType.HISTORY,
                    onClick = { selectedFilter = FilterType.HISTORY },
                    icon = {
                        BadgedBox(badge = {
                            if (historyRecords.isNotEmpty()) Badge { Text("${historyRecords.size}") }
                        }) {
                            Icon(Icons.Default.History, contentDescription = historyLabel)
                        }
                    },
                    label = { Text(historyLabel) }
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedFilter) {
                FilterType.LIVE -> LiveNotificationsList(
                    notifications = groupedActive,
                    onSnooze = { notification ->
                        selectedNotification = notification
                        showSnoozeSheet = true
                    },
                    onDismiss = { notification ->
                        ReFireNotificationListener.dismissNotification(notification)
                    }
                )
                FilterType.DISMISSED -> DismissedNotificationsList(
                    notifications = recentlyDismissed,
                    onSnooze = { notification ->
                        selectedNotification = notification
                        showSnoozeSheet = true
                    }
                )
                FilterType.SNOOZED -> SnoozedList(
                    records = groupedSnoozed,
                    onCancel = { record ->
                        deletedSnoozeRecord = record
                        isDeletedFromHistory = false
                        ReFireNotificationListener.cancelSnooze(record.id)
                        coroutineScope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = snoozeCancelledText,
                                actionLabel = undoText,
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                deletedSnoozeRecord?.let { ReFireNotificationListener.restoreSnooze(it) }
                            }
                            deletedSnoozeRecord = null
                        }
                    },
                    onExtend = { record ->
                        extendingSnooze = record
                        showSnoozeSheet = true
                    },
                    onOpen = { record ->
                        IntentUtils.launchSnooze(context, record)
                    }
                )
                FilterType.HISTORY -> HistoryList(
                    records = historyRecords,
                    onReSnooze = { record ->
                        reSnoozeRecord = record
                        showSnoozeSheet = true
                    },
                    onDelete = { record ->
                        deletedSnoozeRecord = record
                        isDeletedFromHistory = true
                        ReFireNotificationListener.deleteHistoryRecord(record.id)
                        coroutineScope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = historyDeletedText,
                                actionLabel = undoText,
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                deletedSnoozeRecord?.let { ReFireNotificationListener.restoreHistoryRecord(it) }
                            }
                            deletedSnoozeRecord = null
                        }
                    },
                    context = context
                )
            }
        }
    }

    // Snooze bottom sheet
    if (showSnoozeSheet) {
        SnoozeBottomSheet(
            notification = selectedNotification,
            onSnoozeSelected = { preset ->
                val endTime = preset.calculateEndTime()

                when {
                    reSnoozeRecord != null -> {
                        // Re-snooze from history
                        ReFireNotificationListener.reSnoozeFromHistory(reSnoozeRecord!!, endTime)
                    }
                    extendingSnooze != null -> {
                        // Extending existing snooze
                        ReFireNotificationListener.extendSnooze(extendingSnooze!!.id, endTime)
                    }
                    selectedNotification != null -> {
                        // New snooze from notification
                        ReFireNotificationListener.snoozeNotification(selectedNotification!!, endTime)
                    }
                }

                showSnoozeSheet = false
                selectedNotification = null
                extendingSnooze = null
                reSnoozeRecord = null
            },
            onDismiss = {
                showSnoozeSheet = false
                selectedNotification = null
                extendingSnooze = null
                reSnoozeRecord = null
            }
        )
    }
}

@Composable
private fun LiveNotificationsList(
    notifications: List<NotificationInfo>,
    onSnooze: (NotificationInfo) -> Unit,
    onDismiss: (NotificationInfo) -> Unit
) {
    if (notifications.isEmpty()) {
        EmptyStateMessage(
            icon = Icons.Default.Notifications,
            message = stringResource(R.string.empty_live_notifications)
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            items(notifications, key = { "live_${it.getThreadIdentifier()}" }) { notification ->
                NotificationCard(
                    notification = notification,
                    onSnooze = onSnooze,
                    onDismiss = onDismiss
                )
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DismissedNotificationsList(
    notifications: List<NotificationInfo>,
    onSnooze: (NotificationInfo) -> Unit
) {
    if (notifications.isEmpty()) {
        EmptyStateMessage(
            icon = Icons.Default.NotificationsOff,
            message = stringResource(R.string.empty_dismissed_notifications)
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            items(notifications, key = { "dismissed_${it.key}" }) { notification ->
                NotificationCard(notification = notification, onSnooze = onSnooze, isDismissed = true)
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SnoozedList(
    records: List<SnoozeRecord>,
    onCancel: (SnoozeRecord) -> Unit,
    onExtend: (SnoozeRecord) -> Unit,
    onOpen: (SnoozeRecord) -> Unit
) {
    if (records.isEmpty()) {
        EmptyStateMessage(
            icon = Icons.Default.NotificationsOff,
            message = stringResource(R.string.empty_snoozed)
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            items(records, key = { it.id }) { record ->
                SnoozeRecordCard(snooze = record, onCancel = onCancel, onExtend = onExtend, onOpen = onOpen)
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun HistoryList(
    records: List<SnoozeRecord>,
    onReSnooze: (SnoozeRecord) -> Unit,
    onDelete: (SnoozeRecord) -> Unit,
    context: Context
) {
    if (records.isEmpty()) {
        EmptyStateMessage(
            icon = Icons.Default.History,
            message = stringResource(R.string.empty_history)
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            items(records, key = { "history_${it.id}" }) { record ->
                HistoryRecordCard(
                    record = record,
                    onReSnooze = onReSnooze,
                    onDelete = onDelete,
                    onOpen = { IntentUtils.launchSnooze(context, it) }
                )
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun EmptyStateMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
