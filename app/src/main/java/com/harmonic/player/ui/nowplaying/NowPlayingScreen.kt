package com.harmonic.player.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.harmonic.player.data.SongDao
import com.harmonic.player.playback.PlayerController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playerController: PlayerController,
    dao: SongDao,
    onBack: () -> Unit,
    onOpenEqualizer: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val state by playerController.uiState.collectAsState()
    var sliderPosition by remember { mutableStateOf(0f) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var lyricsResult by remember { mutableStateOf<com.harmonic.player.data.LyricsResult>(com.harmonic.player.data.LyricsResult.NotFound) }

    // Recarrega a letra sempre que a música atual mudar. A leitura do
    // arquivo .lrc/.txt é rápida, mas ainda assim roda fora da thread
    // principal pra nunca travar a UI.
    LaunchedEffect(state.currentSong?.id) {
        val song = state.currentSong
        lyricsResult = if (song != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.harmonic.player.data.LyricsRepository.loadLyrics(song)
            }
        } else {
            com.harmonic.player.data.LyricsResult.NotFound
        }
    }

    // Garante que a barra já abra na posição correta mesmo se a música
    // estiver pausada (antes, só atualizava dentro do loop de "tocando").
    LaunchedEffect(Unit) {
        sliderPosition = playerController.currentPositionMs().toFloat()
    }

    // Atualiza a posição da barra de progresso a cada 500ms enquanto toca
    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) {
            if (!isUserSeeking) sliderPosition = playerController.currentPositionMs().toFloat()
            delay(500)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { showLyrics = !showLyrics }) {
                        Icon(
                            Icons.Filled.Subject,
                            contentDescription = if (showLyrics) "Mostrar capa" else "Mostrar letra",
                            tint = if (showLyrics) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = { showSleepTimerDialog = true }) {
                        Icon(
                            Icons.Filled.Bedtime,
                            contentDescription = "Sleep timer",
                            tint = if (state.sleepTimerEndAt != null) MaterialTheme.colorScheme.primary
                                   else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = onOpenEqualizer) {
                        Icon(Icons.Filled.Equalizer, contentDescription = "Equalizador")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Alterna entre a capa do álbum e a letra sincronizada, conforme
            // o botão na barra superior. A letra usa o espaço restante da
            // tela (weight), a capa mantém proporção quadrada.
            if (showLyrics) {
                LyricsView(
                    lyrics = lyricsResult,
                    positionMs = sliderPosition.toLong(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else {
                com.harmonic.player.ui.common.AlbumArt(
                    song = state.currentSong,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp))
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                state.currentSong?.title ?: "Nada tocando",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                state.currentSong?.artist ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f)
            )

            // Info técnica: bitrate, formato, frequência
            state.currentSong?.let { song ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "${song.format} • ${song.bitrate?.let { "${it / 1000} kbps" } ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(Modifier.height(16.dp))

            Slider(
                value = sliderPosition,
                onValueChange = { isUserSeeking = true; sliderPosition = it },
                onValueChangeFinished = {
                    playerController.seekTo(sliderPosition.toLong())
                    isUserSeeking = false
                },
                valueRange = 0f..(state.durationMs.coerceAtLeast(1)).toFloat()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatDuration(sliderPosition.toLong()), style = MaterialTheme.typography.labelSmall)
                Text(formatDuration(state.durationMs), style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { playerController.setShuffle(!state.shuffleEnabled) }) {
                    Icon(Icons.Filled.Shuffle, contentDescription = "Aleatório")
                }
                IconButton(onClick = { playerController.skipPrevious() }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior", modifier = Modifier.size(40.dp))
                }
                FilledIconButton(
                    onClick = { playerController.togglePlayPause() },
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(onClick = { playerController.skipNext() }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Próxima", modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = { playerController.cycleRepeatMode() }) {
                    Icon(Icons.Filled.Repeat, contentDescription = "Repetir")
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { playerController.setPointA() }) {
                    Text(
                        "A" + if (state.pointA != null) " ✓" else "",
                        color = if (state.pointA != null) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
                TextButton(
                    onClick = { playerController.setPointB() },
                    enabled = state.pointA != null
                ) {
                    Text(
                        "B" + if (state.pointB != null) " ✓" else "",
                        color = if (state.pointB != null) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
                if (state.pointA != null || state.pointB != null) {
                    IconButton(onClick = { playerController.clearABRepeat() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Limpar A-B")
                    }
                }

                Spacer(Modifier.width(16.dp))

                IconButton(onClick = {
                    val song = state.currentSong ?: return@IconButton
                    scope.launch {
                        dao.insertBookmark(
                            com.harmonic.player.data.Bookmark(
                                songId = song.id,
                                positionMs = playerController.currentPositionMs(),
                                label = formatDuration(playerController.currentPositionMs())
                            )
                        )
                    }
                }) {
                    Icon(Icons.Filled.BookmarkAdd, contentDescription = "Adicionar marcador")
                }
                IconButton(onClick = { showBookmarksSheet = true }) {
                    Icon(Icons.Filled.Bookmarks, contentDescription = "Ver marcadores")
                }
            }
        }
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            currentEndAt = state.sleepTimerEndAt,
            onDismiss = { showSleepTimerDialog = false },
            onSelectMinutes = { minutes ->
                playerController.startSleepTimer(minutes)
                showSleepTimerDialog = false
            },
            onSelectEndOfSong = {
                playerController.stopAtEndOfSong()
                showSleepTimerDialog = false
            },
            onCancel = {
                playerController.cancelSleepTimer()
                showSleepTimerDialog = false
            }
        )
    }

    if (showBookmarksSheet && state.currentSong != null) {
        BookmarksSheet(
            song = state.currentSong!!,
            dao = dao,
            onDismiss = { showBookmarksSheet = false },
            onSeekTo = { positionMs ->
                playerController.seekTo(positionMs)
                showBookmarksSheet = false
            }
        )
    }
}

@Composable
private fun BookmarksSheet(
    song: com.harmonic.player.data.Song,
    dao: SongDao,
    onDismiss: () -> Unit,
    onSeekTo: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    val bookmarks by dao.getBookmarksForSong(song.id).collectAsState(initial = emptyList())

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                "Marcadores — ${song.title}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
            if (bookmarks.isEmpty()) {
                Text(
                    "Nenhum marcador salvo ainda. Toque no ícone de marcador\n" +
                    "durante a reprodução pra guardar o instante atual.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                bookmarks.forEach { bookmark ->
                    ListItem(
                        headlineContent = { Text(bookmark.label) },
                        leadingContent = { Icon(Icons.Filled.Bookmark, contentDescription = null) },
                        trailingContent = {
                            IconButton(onClick = { scope.launch { dao.deleteBookmark(bookmark.id) } }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remover marcador")
                            }
                        },
                        modifier = Modifier.clickable { onSeekTo(bookmark.positionMs) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepTimerDialog(
    currentEndAt: Long?,
    onDismiss: () -> Unit,
    onSelectMinutes: (Int) -> Unit,
    onSelectEndOfSong: () -> Unit,
    onCancel: () -> Unit
) {
    val options = listOf(5, 10, 15, 30, 45, 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                if (currentEndAt != null) {
                    Text(
                        "Timer ativo. Toque em \"Cancelar\" pra desativar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                }
                options.forEach { minutes ->
                    TextButton(onClick = { onSelectMinutes(minutes) }, modifier = Modifier.fillMaxWidth()) {
                        Text("$minutes minutos", modifier = Modifier.fillMaxWidth())
                    }
                }
                TextButton(onClick = onSelectEndOfSong, modifier = Modifier.fillMaxWidth()) {
                    Text("Fim da música atual", modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            if (currentEndAt != null) {
                TextButton(onClick = onCancel) { Text("Cancelar timer") }
            } else {
                TextButton(onClick = onDismiss) { Text("Fechar") }
            }
        }
    )
}

/** Formata milissegundos como "m:ss", ex: 1234000ms -> "20:34". */
private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
