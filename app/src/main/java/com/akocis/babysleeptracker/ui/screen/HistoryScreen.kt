package com.akocis.babysleeptracker.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akocis.babysleeptracker.viewmodel.HistoryItem
import com.akocis.babysleeptracker.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private sealed class HistoryListItem {
    data class DateHeader(val date: LocalDate) : HistoryListItem()
    data class Entry(val item: HistoryItem) : HistoryListItem()
}

private val DATE_HEADER_FMT = DateTimeFormatter.ofPattern("EEE, MMM d")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onEditEntry: (HistoryItem) -> Unit = {}
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val showDuration by viewModel.showDuration.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dayStartHour = viewModel.dayStartHour
    val dayEndHour = viewModel.dayEndHour
    var showDatePicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Long)
            viewModel.dismissError()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadEntries()
    }

    // Build flat list with date separators
    val listItems = remember(entries) {
        val result = mutableListOf<HistoryListItem>()
        var lastDate: LocalDate? = null
        entries.forEach { item ->
            if (item.date != lastDate) {
                result.add(HistoryListItem.DateHeader(item.date))
                lastDate = item.date
            }
            result.add(HistoryListItem.Entry(item))
        }
        result
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = "Jump to date",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = { viewModel.toggleShowDuration() }) {
                        Icon(
                            if (showDuration) Icons.Default.AccessTime else Icons.Default.Timelapse,
                            contentDescription = if (showDuration) "Show end times" else "Show durations",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.syncAndRefresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
        // Date picker dialog
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = System.currentTimeMillis()
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                val selected = Instant.ofEpochMilli(millis)
                                    .atZone(ZoneId.of("UTC"))
                                    .toLocalDate()
                                var index = listItems.indexOfFirst {
                                    it is HistoryListItem.DateHeader && it.date == selected
                                }
                                if (index < 0) {
                                    // Find nearest date header
                                    val headers = listItems.mapIndexedNotNull { i, item ->
                                        if (item is HistoryListItem.DateHeader) i to item.date else null
                                    }
                                    if (headers.isNotEmpty()) {
                                        index = headers.minBy { kotlin.math.abs(it.second.toEpochDay() - selected.toEpochDay()) }.first
                                    }
                                }
                                if (index >= 0) {
                                    scope.launch { listState.scrollToItem(index) }
                                }
                            }
                            showDatePicker = false
                        }
                    ) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        if (entries.isEmpty() && !isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No entries yet",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        } else if (entries.isNotEmpty()) {
            val totalItems = listItems.size

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = listItems,
                        key = { item ->
                            when (item) {
                                is HistoryListItem.DateHeader -> "date_${item.date}"
                                is HistoryListItem.Entry -> "entry_${item.item.id}"
                            }
                        }
                    ) { listItem ->
                        when (listItem) {
                            is HistoryListItem.DateHeader -> {
                                val today = LocalDate.now()
                                val label = when (listItem.date) {
                                    today -> "Today"
                                    today.minusDays(1) -> "Yesterday"
                                    else -> listItem.date.format(DATE_HEADER_FMT)
                                }
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                                )
                            }
                            is HistoryListItem.Entry -> {
                                HistoryCard(
                                    item = listItem.item,
                                    showDuration = showDuration,
                                    dayStartHour = dayStartHour,
                                    dayEndHour = dayEndHour,
                                    onEdit = { onEditEntry(it) },
                                    onDelete = {
                                        val deletedLine = it.rawLine
                                        val deletedText = it.displayText
                                        viewModel.deleteEntry(it)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "Deleted: $deletedText",
                                                actionLabel = "Undo",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel.reAddEntry(deletedLine)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Draggable scrollbar
                if (totalItems > 10) {
                    val thumbHeightDp = 48.dp
                    val scrollProgress by remember {
                        derivedStateOf {
                            val info = listState.layoutInfo
                            if (info.totalItemsCount <= 1) 0f
                            else listState.firstVisibleItemIndex.toFloat() / (info.totalItemsCount - 1).toFloat()
                        }
                    }

                    BoxWithConstraints(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .fillMaxHeight()
                            .width(14.dp)
                            .padding(vertical = 4.dp)
                            .pointerInput(totalItems) {
                                detectTapGestures { offset ->
                                    val fraction = (offset.y / size.height).coerceIn(0f, 1f)
                                    val target = (fraction * (totalItems - 1)).toInt()
                                    scope.launch { listState.scrollToItem(target) }
                                }
                            }
                            .pointerInput(totalItems) {
                                detectVerticalDragGestures { change, _ ->
                                    change.consume()
                                    val fraction = (change.position.y / size.height).coerceIn(0f, 1f)
                                    val target = (fraction * (totalItems - 1)).toInt()
                                    scope.launch { listState.scrollToItem(target) }
                                }
                            }
                    ) {
                        val trackHeight = maxHeight
                        val thumbHeightPx = with(LocalDensity.current) { thumbHeightDp.toPx() }
                        val trackPx = with(LocalDensity.current) { trackHeight.toPx() }
                        val availablePx = (trackPx - thumbHeightPx).coerceAtLeast(0f)
                        val offsetPx = (scrollProgress * availablePx).toInt()

                        // Track
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        )
                        // Thumb
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(thumbHeightDp)
                                .offset { IntOffset(0, offsetPx) }
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        )
                    }
                }
            }
        }
        }
    }
}

private fun isNightHour(hour: Int, dayStartHour: Int, dayEndHour: Int): Boolean {
    return if (dayStartHour < dayEndHour) {
        hour < dayStartHour || hour >= dayEndHour
    } else {
        hour >= dayEndHour && hour < dayStartHour
    }
}

@Composable
private fun HistoryCard(
    item: HistoryItem,
    showDuration: Boolean,
    dayStartHour: Int,
    dayEndHour: Int,
    onEdit: (HistoryItem) -> Unit,
    onDelete: (HistoryItem) -> Unit
) {
    val isNight = isNightHour(item.hour, dayStartHour, dayEndHour)
    val cardColor = if (isNight) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clickable { onEdit(item) },
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (showDuration && item.durationText != null) item.durationText else item.displayText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onDelete(item) }) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
