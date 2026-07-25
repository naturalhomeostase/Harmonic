package com.harmonic.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Barrinha fina na cor de destaque (com transparência) do lado direito da
 * lista — arrasta pra rolar rápido em vez de precisar dar vários toques
 * pra chegar no fim de uma lista grande. Só some com listas curtas (não
 * vale a pena arrastar quando cabe tudo numa tela só).
 *
 * Usar dentro de um Box, sobrepondo a lista:
 * ```
 * Box(Modifier.fillMaxSize()) {
 *     LazyColumn(state = listState) { ... }
 *     FastScrollbar(listState, items.size, Modifier.align(Alignment.CenterEnd))
 * }
 * ```
 */
@Composable
fun FastScrollbar(
    listState: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier
) {
    if (itemCount < 25) return

    val accent = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var trackHeightPx by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    val layoutInfo = listState.layoutInfo
    val visibleCount = layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    val scrollableRange = (itemCount - visibleCount).coerceAtLeast(1)
    val thumbFraction = (visibleCount.toFloat() / itemCount).coerceIn(0.08f, 1f)
    val scrollFraction = (listState.firstVisibleItemIndex.toFloat() / scrollableRange).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(28.dp)
            .onSizeChanged { trackHeightPx = it.height.toFloat() }
            .pointerInput(itemCount, visibleCount) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false }
                ) { change, _ ->
                    change.consume()
                    if (trackHeightPx <= 0f) return@detectDragGestures
                    val usableHeight = trackHeightPx * (1f - thumbFraction)
                    val thumbTopPx = (change.position.y - (trackHeightPx * thumbFraction / 2f))
                        .coerceIn(0f, usableHeight.coerceAtLeast(0f))
                    val frac = if (usableHeight > 0f) thumbTopPx / usableHeight else 0f
                    val targetIndex = (frac * scrollableRange).toInt().coerceIn(0, itemCount - 1)
                    scope.launch { listState.scrollToItem(targetIndex) }
                }
            }
    ) {
        val thumbOffsetPx = trackHeightPx * (1f - thumbFraction) * scrollFraction
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = with(density) { thumbOffsetPx.toDp() })
                .fillMaxHeight(thumbFraction)
                .width(if (dragging) 5.dp else 3.dp)
                .background(accent.copy(alpha = if (dragging) 0.85f else 0.45f), RoundedCornerShape(3.dp))
        )
    }
}
