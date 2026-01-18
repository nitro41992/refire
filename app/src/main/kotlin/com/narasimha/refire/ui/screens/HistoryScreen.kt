package com.narasimha.refire.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.narasimha.refire.R
import com.narasimha.refire.data.model.SnoozeRecord
import com.narasimha.refire.data.model.SnoozeSource
import com.narasimha.refire.ui.components.HistoryRecordCard
import com.narasimha.refire.ui.theme.AppNameTextStyle

private enum class HistoryFilter(val label: String) {
    SHARED("Shared"),
    NOTIFICATIONS("Notifications")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    records: List<SnoozeRecord>,
    onReSnooze: (SnoozeRecord) -> Unit,
    onClick: (SnoozeRecord) -> Unit,
    onIgnore: (SnoozeRecord) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var selectedFilter by remember { mutableStateOf<HistoryFilter?>(null) }
    var showConvosOnly by remember { mutableStateOf(false) }

    // Filter records based on selection
    val filteredRecords = remember(records, selectedFilter, showConvosOnly) {
        var result = when (selectedFilter) {
            null -> records
            HistoryFilter.SHARED -> records.filter { it.source == SnoozeSource.SHARE_SHEET }
            HistoryFilter.NOTIFICATIONS -> records.filter { it.source == SnoozeSource.NOTIFICATION }
        }
        if (showConvosOnly) result = result.filter { it.isConversation() }
        result
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.subtab_history),
                        style = AppNameTextStyle.copy(color = Color.White),
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null // No ripple on title tap
                        ) {
                            coroutineScope.launch { listState.animateScrollToItem(0) }
                        }
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
        ) {
            // Filter chips row (matching Feed/Schedule structure)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(HistoryFilter.entries.toList()) { filter ->
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
            }

            // List content
            if (filteredRecords.isEmpty()) {
                EmptyStateMessage(
                    icon = Icons.Default.History,
                    message = stringResource(R.string.empty_history)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = filteredRecords,
                        key = { "history_${it.id}" },
                        contentType = { "HistoryRecordCard" }
                    ) { record ->
                        HistoryRecordCard(
                            record = record,
                            onReSnooze = onReSnooze,
                            onClick = onClick,
                            onLongPress = onIgnore,
                            modifier = Modifier.animateItem()
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
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
