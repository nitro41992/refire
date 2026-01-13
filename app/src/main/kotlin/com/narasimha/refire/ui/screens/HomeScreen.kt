package com.narasimha.refire.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isServiceConnected by remember { mutableStateOf(ReFireNotificationListener.isConnected()) }
    var activeNotifications by remember { mutableStateOf<List<NotificationInfo>>(emptyList()) }
    var recentlyDismissed by remember { mutableStateOf<List<NotificationInfo>>(emptyList()) }
    var snoozeRecords by remember { mutableStateOf<List<SnoozeRecord>>(emptyList()) }
    var historyRecords by remember { mutableStateOf<List<SnoozeRecord>>(emptyList()) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Sub-tab state for Active tab (0 = Live, 1 = Dismissed)
    var activeSubTab by remember { mutableIntStateOf(0) }
    // Sub-tab state for Stash tab (0 = Snoozed, 1 = History)
    var stashSubTab by remember { mutableIntStateOf(0) }

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

    // Observe service events
    LaunchedEffect(Unit) {
        ReFireNotificationListener.notificationEvents.collectLatest { event ->
            when (event) {
                is ReFireNotificationListener.NotificationEvent.ServiceConnected -> {
                    isServiceConnected = true
                }
                is ReFireNotificationListener.NotificationEvent.ServiceDisconnected -> {
                    isServiceConnected = false
                }
                else -> {}
            }
        }
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Service status card
            ServiceStatusCard(
                isConnected = isServiceConnected,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.tab_active))
                            val totalCount = activeNotifications.size + recentlyDismissed.size
                            if (totalCount > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "($totalCount)",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.tab_stash))
                            val stashCount = snoozeRecords.size + historyRecords.size
                            if (stashCount > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "($stashCount)",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                )
            }

            // Tab content
            when (selectedTabIndex) {
                0 -> ActiveNotificationsTab(
                    activeNotifications = activeNotifications,
                    recentlyDismissed = recentlyDismissed,
                    selectedSubTab = activeSubTab,
                    onSubTabSelected = { activeSubTab = it },
                    onSnooze = { notification ->
                        selectedNotification = notification
                        showSnoozeSheet = true
                    }
                )
                1 -> StashTab(
                    snoozeRecords = snoozeRecords,
                    historyRecords = historyRecords,
                    selectedSubTab = stashSubTab,
                    onSubTabSelected = { stashSubTab = it },
                    onCancel = { record ->
                        // Save record for potential undo
                        deletedSnoozeRecord = record
                        isDeletedFromHistory = false
                        // Perform cancel
                        ReFireNotificationListener.cancelSnooze(record.id)
                        // Show snackbar with undo
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
                    },
                    onReSnooze = { record ->
                        reSnoozeRecord = record
                        showSnoozeSheet = true
                    },
                    onDeleteHistory = { record ->
                        // Save record for potential undo
                        deletedSnoozeRecord = record
                        isDeletedFromHistory = true
                        // Perform delete
                        ReFireNotificationListener.deleteHistoryRecord(record.id)
                        // Show snackbar with undo
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
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveNotificationsTab(
    activeNotifications: List<NotificationInfo>,
    recentlyDismissed: List<NotificationInfo>,
    selectedSubTab: Int,
    onSubTabSelected: (Int) -> Unit,
    onSnooze: (NotificationInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    // Apply grouping only to active notifications (mirrors system tray)
    val groupedActive = remember(activeNotifications) {
        activeNotifications.groupNotificationsByThread()
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Segmented button row
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SegmentedButton(
                selected = selectedSubTab == 0,
                onClick = { onSubTabSelected(0) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {}
            ) {
                Text("${stringResource(R.string.subtab_live)} (${groupedActive.size})")
            }
            SegmentedButton(
                selected = selectedSubTab == 1,
                onClick = { onSubTabSelected(1) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {}
            ) {
                Text("${stringResource(R.string.subtab_dismissed)} (${recentlyDismissed.size})")
            }
        }

        // Content based on selection
        when (selectedSubTab) {
            0 -> LiveNotificationsList(groupedActive, onSnooze)
            1 -> DismissedNotificationsList(recentlyDismissed, onSnooze)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StashTab(
    snoozeRecords: List<SnoozeRecord>,
    historyRecords: List<SnoozeRecord>,
    selectedSubTab: Int,
    onSubTabSelected: (Int) -> Unit,
    onCancel: (SnoozeRecord) -> Unit,
    onExtend: (SnoozeRecord) -> Unit,
    onOpen: (SnoozeRecord) -> Unit,
    onReSnooze: (SnoozeRecord) -> Unit,
    onDeleteHistory: (SnoozeRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    // Apply grouping to snooze records
    val groupedRecords = remember(snoozeRecords) {
        snoozeRecords.groupSnoozesByThread()
    }
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize()) {
        // Segmented button row
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SegmentedButton(
                selected = selectedSubTab == 0,
                onClick = { onSubTabSelected(0) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {}
            ) {
                Text("${stringResource(R.string.subtab_snoozed)} (${groupedRecords.size})")
            }
            SegmentedButton(
                selected = selectedSubTab == 1,
                onClick = { onSubTabSelected(1) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {}
            ) {
                Text("${stringResource(R.string.subtab_history)} (${historyRecords.size})")
            }
        }

        // Content based on selection
        when (selectedSubTab) {
            0 -> SnoozedList(groupedRecords, onCancel, onExtend, onOpen)
            1 -> HistoryList(historyRecords, onReSnooze, onDeleteHistory, context)
        }
    }
}

@Composable
private fun LiveNotificationsList(
    notifications: List<NotificationInfo>,
    onSnooze: (NotificationInfo) -> Unit
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
                NotificationCard(notification = notification, onSnooze = onSnooze)
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

@Composable
private fun ServiceStatusCard(
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isConnected) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = if (isConnected) {
                    stringResource(R.string.home_service_active)
                } else {
                    stringResource(R.string.home_service_inactive)
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (isConnected) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
        }
    }
}
