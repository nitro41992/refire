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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.narasimha.refire.ui.components.NotificationCard
import com.narasimha.refire.ui.components.SnoozeBottomSheet
import com.narasimha.refire.ui.components.SnoozeRecordCard
import com.narasimha.refire.ui.util.groupNotificationsByThread
import com.narasimha.refire.ui.util.groupSnoozesByThread
import kotlinx.coroutines.flow.collectLatest

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
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Bottom sheet state
    var showSnoozeSheet by remember { mutableStateOf(false) }
    var selectedNotification by remember { mutableStateOf<NotificationInfo?>(null) }
    var extendingSnooze by remember { mutableStateOf<SnoozeRecord?>(null) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
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
                            if (snoozeRecords.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "(${snoozeRecords.size})",
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
                    onSnooze = { notification ->
                        selectedNotification = notification
                        showSnoozeSheet = true
                    }
                )
                1 -> StashTab(
                    snoozeRecords = snoozeRecords,
                    onCancel = { record ->
                        ReFireNotificationListener.cancelSnooze(record.id)
                    },
                    onExtend = { record ->
                        extendingSnooze = record
                        showSnoozeSheet = true
                    },
                    onOpen = { record ->
                        IntentUtils.launchSnooze(context, record)
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

                if (extendingSnooze != null) {
                    // Extending existing snooze
                    ReFireNotificationListener.extendSnooze(extendingSnooze!!.id, endTime)
                } else if (selectedNotification != null) {
                    // New snooze from notification
                    ReFireNotificationListener.snoozeNotification(selectedNotification!!, endTime)
                }

                showSnoozeSheet = false
                selectedNotification = null
                extendingSnooze = null
            },
            onDismiss = {
                showSnoozeSheet = false
                selectedNotification = null
                extendingSnooze = null
            }
        )
    }
}

@Composable
private fun ActiveNotificationsTab(
    activeNotifications: List<NotificationInfo>,
    recentlyDismissed: List<NotificationInfo>,
    onSnooze: (NotificationInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    // Apply grouping to both lists
    val groupedActive = remember(activeNotifications) {
        activeNotifications.groupNotificationsByThread()
    }
    val groupedRecents = remember(recentlyDismissed) {
        recentlyDismissed.groupNotificationsByThread()
    }

    if (groupedActive.isEmpty() && groupedRecents.isEmpty()) {
        EmptyStateMessage(
            icon = Icons.Default.Notifications,
            message = stringResource(R.string.empty_active_notifications)
        )
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Active notifications (grouped by thread)
            items(groupedActive, key = { "active_${it.getThreadIdentifier()}" }) { notification ->
                NotificationCard(
                    notification = notification,
                    onSnooze = onSnooze
                )
            }

            // Recently dismissed section (grouped by thread)
            if (groupedRecents.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.section_recently_dismissed),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                items(groupedRecents, key = { "dismissed_${it.getThreadIdentifier()}" }) { notification ->
                    NotificationCard(
                        notification = notification,
                        onSnooze = onSnooze,
                        isDismissed = true
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun StashTab(
    snoozeRecords: List<SnoozeRecord>,
    onCancel: (SnoozeRecord) -> Unit,
    onExtend: (SnoozeRecord) -> Unit,
    onOpen: (SnoozeRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    // Apply grouping to snooze records
    val groupedRecords = remember(snoozeRecords) {
        snoozeRecords.groupSnoozesByThread()
    }

    if (groupedRecords.isEmpty()) {
        EmptyStateMessage(
            icon = Icons.Default.NotificationsOff,
            message = stringResource(R.string.empty_stash)
        )
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Display grouped snooze records
            items(groupedRecords, key = { it.id }) { record ->
                SnoozeRecordCard(
                    snooze = record,
                    onCancel = onCancel,
                    onExtend = onExtend,
                    onOpen = onOpen
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
