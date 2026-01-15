package com.narasimha.refire.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.narasimha.refire.R
import com.narasimha.refire.core.util.IntentUtils
import com.narasimha.refire.ui.theme.AppNameTextStyle
import com.narasimha.refire.data.model.NotificationInfo
import com.narasimha.refire.data.model.SnoozePreset
import com.narasimha.refire.data.model.SnoozeRecord
import com.narasimha.refire.data.model.SnoozeSource
import com.narasimha.refire.data.model.SnoozeStatus
import com.narasimha.refire.service.ReFireNotificationListener
import com.narasimha.refire.ui.components.DismissedNotificationCard
import com.narasimha.refire.ui.components.HoldToConfirmButton
import com.narasimha.refire.ui.components.NotificationCard
import com.narasimha.refire.ui.components.SnoozeBottomSheet
import com.narasimha.refire.ui.components.SnoozeRecordCard
import com.narasimha.refire.ui.util.filterOutExpiredMessages
import com.narasimha.refire.ui.util.groupNotificationsByThread
import com.narasimha.refire.ui.util.groupSnoozesByThread
import android.widget.Toast
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private enum class FilterType {
    LIVE, SNOOZED
}

private enum class SourceFilter(val label: String) {
    SHARED("Shared"),
    NOTIFICATIONS("Notifications")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier
) {
    var activeNotifications by remember { mutableStateOf<List<NotificationInfo>>(emptyList()) }
    var snoozeRecords by remember { mutableStateOf<List<SnoozeRecord>>(emptyList()) }
    var historyRecords by remember { mutableStateOf<List<SnoozeRecord>>(emptyList()) }
    var dismissedRecords by remember { mutableStateOf<List<SnoozeRecord>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf(FilterType.LIVE) }

    // Scroll states for tap-to-scroll-top on nav bar items
    val liveListState = rememberLazyListState()
    val snoozedListState = rememberLazyListState()

    // Bottom sheet state
    var showSnoozeSheet by remember { mutableStateOf(false) }
    var selectedNotification by remember { mutableStateOf<NotificationInfo?>(null) }
    var extendingSnooze by remember { mutableStateOf<SnoozeRecord?>(null) }
    var reSnoozeRecord by remember { mutableStateOf<SnoozeRecord?>(null) }

    // History screen navigation state
    var showHistoryScreen by remember { mutableStateOf(false) }

    // Snackbar state for undo functionality
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var deletedSnoozeRecord by remember { mutableStateOf<SnoozeRecord?>(null) }

    // Observe active notifications
    LaunchedEffect(Unit) {
        ReFireNotificationListener.activeNotifications.collectLatest { notifications ->
            activeNotifications = notifications
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

    // Observe dismissed records (for LIVE tab)
    LaunchedEffect(Unit) {
        ReFireNotificationListener.dismissedRecords.collectLatest { records ->
            dismissedRecords = records
        }
    }

    // Get string resources for snackbar (need to be outside coroutine)
    val snoozeDismissedText = stringResource(R.string.action_dismiss)
    val undoText = stringResource(R.string.snackbar_undo)

    // Apply grouping on background thread (outside Scaffold for bottom bar badge counts)
    val groupedActive by produceState(initialValue = emptyList<NotificationInfo>(), key1 = activeNotifications) {
        value = withContext(Dispatchers.Default) {
            activeNotifications.groupNotificationsByThread()
        }
    }
    val groupedSnoozed by produceState(initialValue = emptyList<SnoozeRecord>(), key1 = snoozeRecords) {
        value = withContext(Dispatchers.Default) {
            snoozeRecords.groupSnoozesByThread()
        }
    }

    // Badge count using derivedStateOf to avoid recomposition when list changes but size doesn't
    val snoozedBadgeCount by remember { derivedStateOf { groupedSnoozed.size } }

    // Navigation item labels
    val liveLabel = stringResource(R.string.subtab_live)
    val snoozedLabel = stringResource(R.string.subtab_snoozed)

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = AppNameTextStyle
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedFilter == FilterType.LIVE,
                    onClick = {
                        if (selectedFilter == FilterType.LIVE) {
                            // Already selected - scroll to top
                            coroutineScope.launch { liveListState.animateScrollToItem(0) }
                        } else {
                            selectedFilter = FilterType.LIVE
                        }
                    },
                    icon = {
                        Icon(Icons.Default.Notifications, contentDescription = liveLabel)
                    },
                    label = { Text(liveLabel) }
                )
                NavigationBarItem(
                    selected = selectedFilter == FilterType.SNOOZED,
                    onClick = {
                        if (selectedFilter == FilterType.SNOOZED) {
                            // Already selected - scroll to top
                            coroutineScope.launch { snoozedListState.animateScrollToItem(0) }
                        } else {
                            selectedFilter = FilterType.SNOOZED
                        }
                    },
                    icon = {
                        BadgedBox(badge = {
                            if (snoozedBadgeCount > 0) Badge { Text("$snoozedBadgeCount") }
                        }) {
                            Icon(Icons.Default.Schedule, contentDescription = snoozedLabel)
                        }
                    },
                    label = { Text(snoozedLabel) }
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        val context = LocalContext.current
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedFilter) {
                FilterType.LIVE -> LiveNotificationsList(
                    listState = liveListState,
                    activeNotifications = groupedActive,
                    dismissedRecords = dismissedRecords,
                    expiredHistory = historyRecords,
                    onSnooze = { notification ->
                        selectedNotification = notification
                        showSnoozeSheet = true
                    },
                    onDismiss = { notification ->
                        // Remove immediately - animation plays while item is removed from list
                        ReFireNotificationListener.dismissNotification(notification)
                    },
                    onDismissAll = {
                        ReFireNotificationListener.dismissAllNotifications()
                    },
                    onReSnooze = { record ->
                        reSnoozeRecord = record
                        showSnoozeSheet = true
                    },
                    onNotificationClick = { notification ->
                        val launched = IntentUtils.launchNotification(context, notification)
                        if (!launched) {
                            Toast.makeText(
                                context,
                                "Cannot open ${notification.appName}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onDismissedClick = { record ->
                        IntentUtils.launchSnooze(context, record)
                    }
                )
                FilterType.SNOOZED -> SnoozedList(
                    listState = snoozedListState,
                    records = groupedSnoozed,
                    onDismiss = { record ->
                        deletedSnoozeRecord = record
                        ReFireNotificationListener.dismissSnooze(record.id)
                        coroutineScope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = snoozeDismissedText,
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
                    onClick = { record ->
                        IntentUtils.launchSnooze(context, record)
                    },
                    onHistoryClick = { showHistoryScreen = true }
                )
            }
        }
    }

    // History screen (full-screen overlay with slide animation)
    AnimatedVisibility(
        visible = showHistoryScreen,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(durationMillis = 250)
        ),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(durationMillis = 200)
        )
    ) {
        val context = LocalContext.current
        HistoryScreen(
            records = historyRecords,
            onReSnooze = { record ->
                reSnoozeRecord = record
                showSnoozeSheet = true
            },
            onClick = { record ->
                IntentUtils.launchSnooze(context, record)
            },
            onBack = { showHistoryScreen = false }
        )
    }

    // Snooze bottom sheet
    if (showSnoozeSheet) {
        SnoozeBottomSheet(
            notification = selectedNotification,
            snoozeRecord = extendingSnooze ?: reSnoozeRecord,
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
private fun LiveNotificationsList(
    listState: LazyListState,
    activeNotifications: List<NotificationInfo>,
    dismissedRecords: List<SnoozeRecord>,
    expiredHistory: List<SnoozeRecord>,
    onSnooze: (NotificationInfo) -> Unit,
    onDismiss: (NotificationInfo) -> Unit,
    onDismissAll: () -> Unit,
    onReSnooze: (SnoozeRecord) -> Unit,
    onNotificationClick: (NotificationInfo) -> Unit,
    onDismissedClick: (SnoozeRecord) -> Unit
) {
    // Convos filter state
    var showConvosOnly by remember { mutableStateOf(false) }

    // Filter Active notifications to remove messages already captured in EXPIRED history
    // This creates a "new lifecycle" where only messages after snooze expired are shown
    val filteredActive = remember(activeNotifications, expiredHistory) {
        activeNotifications.filterOutExpiredMessages(expiredHistory)
    }

    // Sort each list independently, applying convos filter
    val sortedActive = remember(filteredActive, showConvosOnly) {
        val filtered = if (showConvosOnly) filteredActive.filter { it.isConversation() } else filteredActive
        filtered.sortedByDescending { it.postTime }
    }
    val groupedDismissed = remember(dismissedRecords, showConvosOnly) {
        val filtered = if (showConvosOnly) dismissedRecords.filter { it.isConversation() } else dismissedRecords
        filtered.groupSnoozesByThread().sortedByDescending { it.createdAt }
    }

    val totalItems = sortedActive.size + groupedDismissed.size
    val footerHintText = stringResource(R.string.hint_live_footer)
    val dismissedLabel = stringResource(R.string.filter_dismissed)
    val activeEmptyText = stringResource(R.string.section_active_empty)
    val dismissedEmptyText = stringResource(R.string.section_dismissed_empty)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top spacer
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Filter chips row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    FilterChip(
                        selected = showConvosOnly,
                        onClick = { showConvosOnly = !showConvosOnly },
                        label = { Text(stringResource(R.string.filter_convos)) },
                        leadingIcon = if (showConvosOnly) {
                            { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                        } else null
                    )
                }
            }

            // Dismiss all button (only when 2+ active notifications)
            if (sortedActive.size >= 2) {
                item(key = "dismiss_all") {
                    HoldToConfirmButton(
                        onConfirm = onDismissAll,
                        modifier = Modifier.animateItem()
                    )
                }
            }

            // Active notifications section
            if (sortedActive.isNotEmpty()) {
                items(
                    items = sortedActive,
                    key = { "active_${it.getThreadIdentifier()}" },
                    contentType = { "NotificationCard" }
                ) { notification ->
                    NotificationCard(
                        notification = notification,
                        onSnooze = onSnooze,
                        onDismiss = onDismiss,
                        onClick = onNotificationClick,
                        modifier = Modifier.animateItem()
                    )
                }
            } else {
                item {
                    SectionEmptyState(message = activeEmptyText)
                }
            }

            // Dismissed section header
            item {
                SectionDivider(label = dismissedLabel)
            }

            // Dismissed notifications section
            if (groupedDismissed.isNotEmpty()) {
                items(
                    items = groupedDismissed,
                    key = { "dismissed_${it.id}" },
                    contentType = { "DismissedNotificationCard" }
                ) { record ->
                    DismissedNotificationCard(
                        record = record,
                        onReSnooze = onReSnooze,
                        onClick = onDismissedClick,
                        modifier = Modifier.animateItem()
                    )
                }
            } else {
                item {
                    SectionEmptyState(message = dismissedEmptyText)
                }
            }

            // Extra space at bottom for footer hint
            item { Spacer(modifier = Modifier.height(56.dp)) }
        }
        if (totalItems <= 3) {
            FooterHint(
                message = footerHintText,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun SectionDivider(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

@Composable
private fun SectionEmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnoozedList(
    listState: LazyListState,
    records: List<SnoozeRecord>,
    onDismiss: (SnoozeRecord) -> Unit,
    onExtend: (SnoozeRecord) -> Unit,
    onClick: (SnoozeRecord) -> Unit,
    onHistoryClick: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf<SourceFilter?>(null) }
    var showConvosOnly by remember { mutableStateOf(false) }
    val historyLabel = stringResource(R.string.subtab_history_label)

    // Filter records based on selection
    val filteredRecords = remember(records, selectedFilter, showConvosOnly) {
        var result = when (selectedFilter) {
            null -> records
            SourceFilter.SHARED -> records.filter { it.source == SnoozeSource.SHARE_SHEET }
            SourceFilter.NOTIFICATIONS -> records.filter { it.source == SnoozeSource.NOTIFICATION }
        }
        if (showConvosOnly) result = result.filter { it.isConversation() }
        result
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter chips row with history icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SourceFilter.entries.toList()) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = {
                            // Toggle: clicking selected chip deselects it
                            selectedFilter = if (selectedFilter == filter) null else filter
                        },
                        label = { Text(filter.label) },
                        leadingIcon = if (selectedFilter == filter) {
                            { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                        } else null
                    )
                }
                item {
                    FilterChip(
                        selected = showConvosOnly,
                        onClick = { showConvosOnly = !showConvosOnly },
                        label = { Text(stringResource(R.string.filter_convos)) },
                        leadingIcon = if (showConvosOnly) {
                            { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                        } else null
                    )
                }
            }
            // History chip matching filter pill style
            AssistChip(
                onClick = onHistoryClick,
                label = { Text(historyLabel) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }

        // List content
        if (filteredRecords.isEmpty()) {
            EmptyStateMessage(
                icon = Icons.Default.Schedule,
                message = stringResource(R.string.empty_snoozed)
            )
        } else {
            val footerHintText = stringResource(R.string.hint_snoozed_footer)
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = filteredRecords,
                        key = { it.id },
                        contentType = { "SnoozeRecordCard" }
                    ) { record ->
                        SnoozeRecordCard(
                            snooze = record,
                            onDismiss = onDismiss,
                            onExtend = onExtend,
                            onClick = onClick,
                            modifier = Modifier.animateItem()
                        )
                    }
                    // Extra space at bottom for footer hint
                    item { Spacer(modifier = Modifier.height(56.dp)) }
                }
                if (filteredRecords.size <= 3) {
                    FooterHint(
                        message = footerHintText,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
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
private fun FooterHint(
    message: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp, horizontal = 32.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Lightbulb,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}
