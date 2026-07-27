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
    // Aparece sempre que a lista realmente PRECISA rolar pra ver todo o
    // conteúdo (não só quando ela passa de um número fixo de itens) — uma
    // lista com poucos itens grandes pode já não caber na tela, e uma com
    // muitos itens pequenos pode caber inteira sem rolar nenhum pixel.
    val canScroll = listState.canScrollForward || listState.canScrollBackward
    if (!canScroll || itemCount <= 0) return

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
                .width(if (dragging) 7.dp else 5.dp)
                .background(accent.copy(alpha = if (dragging) 0.85f else 0.5f), RoundedCornerShape(4.dp))
        )
    }
}

/**
 * Versão da barrinha pra containers com rolagem "simples" (`Modifier.verticalScroll`
 * + `ScrollState`), em vez de Lazy — usada onde a lista não pode ser lazy
 * (ex: a playlist com reordenar por arrasto, que precisa de todos os itens
 * medidos de uma vez).
 */
@Composable
fun FastScrollbarPlain(
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier
) {
    val canScroll = scrollState.canScrollForward || scrollState.canScrollBackward
    if (!canScroll) return

    val accent = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var viewportHeightPx by remember { mutableStateOf(0f) }

    val contentHeightPx = (viewportHeightPx + scrollState.maxValue).coerceAtLeast(1f)
    val thumbFraction = (viewportHeightPx / contentHeightPx).coerceIn(0.08f, 1f)
    val scrollFraction = if (scrollState.maxValue > 0) scrollState.value.toFloat() / scrollState.maxValue else 0f
    var dragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(28.dp)
            .onSizeChanged { viewportHeightPx = it.height.toFloat() }
            .pointerInput(scrollState.maxValue) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false }
                ) { change, _ ->
                    change.consume()
                    if (viewportHeightPx <= 0f) return@detectDragGestures
                    val usableHeight = viewportHeightPx * (1f - thumbFraction)
                    val thumbTopPx = (change.position.y - (viewportHeightPx * thumbFraction / 2f))
                        .coerceIn(0f, usableHeight.coerceAtLeast(0f))
                    val frac = if (usableHeight > 0f) thumbTopPx / usableHeight else 0f
                    scope.launch { scrollState.scrollTo((frac * scrollState.maxValue).toInt()) }
                }
            }
    ) {
        val thumbOffsetPx = viewportHeightPx * (1f - thumbFraction) * scrollFraction
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = with(density) { thumbOffsetPx.toDp() })
                .fillMaxHeight(thumbFraction)
                .width(if (dragging) 7.dp else 5.dp)
                .background(accent.copy(alpha = if (dragging) 0.85f else 0.5f), RoundedCornerShape(4.dp))
        )
    }
}
/**
 * Mesma barrinha de rolagem rápida, só que pra grids (
 * [androidx.compose.foundation.lazy.grid.LazyGridState]) — usada nas
 * visualizações em grade de Artistas/Álbuns. [itemCount] aqui é a
 * quantidade de CÉLULAS (não de linhas).
 */
@Composable
fun FastScrollbarGrid(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    itemCount: Int,
    modifier: Modifier = Modifier
) {
    val canScroll = gridState.canScrollForward || gridState.canScrollBackward
    if (!canScroll || itemCount <= 0) return

    val accent = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var trackHeightPx by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    val layoutInfo = gridState.layoutInfo
    val visibleCount = layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    val scrollableRange = (itemCount - visibleCount).coerceAtLeast(1)
    val thumbFraction = (visibleCount.toFloat() / itemCount).coerceIn(0.08f, 1f)
    val scrollFraction = (gridState.firstVisibleItemIndex.toFloat() / scrollableRange).coerceIn(0f, 1f)

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
                    scope.launch { gridState.scrollToItem(targetIndex) }
                }
            }
    ) {
        val thumbOffsetPx = trackHeightPx * (1f - thumbFraction) * scrollFraction
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = with(density) { thumbOffsetPx.toDp() })
                .fillMaxHeight(thumbFraction)
                .width(if (dragging) 7.dp else 5.dp)
                .background(accent.copy(alpha = if (dragging) 0.85f else 0.5f), RoundedCornerShape(4.dp))
        )
    }
}
