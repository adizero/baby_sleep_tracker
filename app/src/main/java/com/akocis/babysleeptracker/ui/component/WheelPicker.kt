package com.akocis.babysleeptracker.ui.component

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
fun WheelPicker(
    items: List<Int>,
    initialValue: Int,
    onValueChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleItemCount: Int = 5,
    itemHeight: Dp = 48.dp,
    label: String = "ml"
) {
    val halfVisible = visibleItemCount / 2
    val initialIndex = items.indexOf(initialValue).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    val selectedIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = layoutInfo.viewportStartOffset +
                (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2
            layoutInfo.visibleItemsInfo
                .minByOrNull { abs((it.offset + it.size / 2) - viewportCenter) }
                ?.index ?: initialIndex
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling) {
                    val idx = selectedIndex
                    if (idx in items.indices) {
                        onValueChanged(items[idx])
                    }
                }
            }
    }

    val totalHeight = itemHeight * visibleItemCount
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }
    val paddingItems = halfVisible

    Box(modifier = modifier) {
        Row(
            modifier = Modifier.height(totalHeight).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyColumn(
                state = listState,
                flingBehavior = snapBehavior,
                modifier = Modifier
                    .weight(1f)
                    .height(totalHeight)
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        val fadeHeight = itemHeightPx * 1.5f
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startY = 0f,
                                endY = fadeHeight
                            ),
                            blendMode = BlendMode.DstIn
                        )
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = size.height - fadeHeight,
                                endY = size.height
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                // Top padding items
                items(paddingItems) {
                    Box(modifier = Modifier.height(itemHeight))
                }
                items(items.size) { index ->
                    val distance = abs(index - selectedIndex)
                    val alpha = when (distance) {
                        0 -> 1f
                        1 -> 0.6f
                        else -> 0.3f
                    }
                    val scale = when (distance) {
                        0 -> 1f
                        1 -> 0.85f
                        else -> 0.7f
                    }
                    Box(
                        modifier = Modifier
                            .height(itemHeight)
                            .fillMaxWidth()
                            .graphicsLayer(
                                alpha = alpha,
                                scaleX = scale,
                                scaleY = scale
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = items[index].toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = if (distance == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (distance == 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                // Bottom padding items
                items(paddingItems) {
                    Box(modifier = Modifier.height(itemHeight))
                }
            }

            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, end = 16.dp)
            )
        }

        // Selection indicator dividers
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .graphicsLayer(translationY = -itemHeightPx / 2),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .graphicsLayer(translationY = itemHeightPx / 2),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
    }
}
