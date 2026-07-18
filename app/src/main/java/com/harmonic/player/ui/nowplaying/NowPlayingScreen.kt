package com.harmonic.player.ui.nowplaying

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.harmonic.player.data.AlbumArtLoader
import com.harmonic.player.data.SongDao
import com.harmonic.player.playback.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playerController: PlayerController,
    dao: SongDao,
    onBack: () -> Unit,
    onOpenEqualizer: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state by playerController.uiState.collectAsState()
    var sliderPosition by remember { mutableStateOf(0f) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var lyricsResult by remember { mutableStateOf<com.harmonic.player.data.LyricsResult>(com.harmonic.player.data.LyricsResult.NotFound) }

    // Bitmap da capa da música atual — usado em três lugares: fundo desfocado,
    // arte dentro do vinil giratório, e extração de cor (Palette) pra pintar
    // o resto da tela com uma cor que combine com a música tocando.
    val currentSong = state.currentSong
    val albumBitmap by produceState<Bitmap?>(initialValue = null, key1 = currentSong?.id) {
        value = currentSong?.let { AlbumArtLoader.load(context, it) }
    }

    val extractedColor by produceState<Color?>(initialValue = null, key1 = albumBitmap) {
        value = albumBitmap?.let { bmp ->
            withContext(Dispatchers.Default) {
                try {
                    val palette = androidx.palette.graphics.Palette.from(bmp).generate()
                    val swatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch
                        ?: palette.dominantSwatch ?: palette.mutedSwatch
                    swatch?.let { Color(it.rgb) }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    // Clareia um pouco cores extraídas escuras demais, senão o texto/ícones
    // que dependem dela ficam ilegíveis sobre o fundo também escuro.
    val pageAccent = (extractedColor ?: MaterialTheme.colorScheme.primary).let { color ->
        if (color.luminance() < 0.35f) androidx.compose.ui.graphics.lerp(color, Color.White, 0.35f) else color
    }
    val onPageAccent = if (pageAccent.luminance() > 0.6f) Color(0xFF1A1A1A) else Color.White

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

    Box(modifier = Modifier.fillMaxSize()) {
        // Fundo desfocado com a própria capa da música — só aparece quando
        // a música tem capa de verdade; sem capa, o fundo padrão do app
        // (imagem/gradiente escolhido em Aparência) continua por trás,
        // porque o Scaffold logo abaixo é totalmente transparente.
        albumBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(60.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )
        }

        // A cor de destaque desta tela (título, slider, botão de play,
        // ícones ativos...) passa a vir da própria capa em vez da cor de
        // destaque fixa do app — só aqui, o resto do app continua igual.
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(primary = pageAccent),
            typography = MaterialTheme.typography
        ) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Voltar", tint = Color.White.copy(alpha = 0.9f))
                    }
                },
                actions = {
                    IconButton(onClick = { showLyrics = !showLyrics }) {
                        Icon(
                            Icons.Filled.Subject,
                            contentDescription = if (showLyrics) "Mostrar capa" else "Mostrar letra",
                            tint = if (showLyrics) pageAccent else Color.White.copy(alpha = 0.85f)
                        )
                    }
                    IconButton(onClick = { showSleepTimerDialog = true }) {
                        Icon(
                            Icons.Filled.Bedtime,
                            contentDescription = "Sleep timer",
                            tint = if (state.sleepTimerEndAt != null) pageAccent
                                   else Color.White.copy(alpha = 0.85f)
                        )
                    }
                    IconButton(onClick = onOpenEqualizer) {
                        Icon(Icons.Filled.Equalizer, contentDescription = "Equalizador", tint = Color.White.copy(alpha = 0.85f))
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
                VinylRecord(
                    bitmap = albumBitmap,
                    isPlaying = state.isPlaying,
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .aspectRatio(1f)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                state.currentSong?.title ?: "Nada tocando",
                style = MaterialTheme.typography.titleLarge,
                color = pageAccent
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
                    color = Color.White.copy(alpha = 0.55f)
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
                Text(
                    formatDuration(sliderPosition.toLong()),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
                Text(
                    formatDuration(state.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { playerController.setShuffle(!state.shuffleEnabled) }) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Aleatório",
                        tint = if (state.shuffleEnabled) pageAccent else Color.White.copy(alpha = 0.85f)
                    )
                }
                IconButton(onClick = { playerController.skipPrevious() }) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(40.dp)
                    )
                }
                FilledIconButton(
                    onClick = { playerController.togglePlayPause() },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = pageAccent,
                        contentColor = onPageAccent
                    ),
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(onClick = { playerController.skipNext() }) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Próxima",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(onClick = { playerController.cycleRepeatMode() }) {
                    Icon(
                        Icons.Filled.Repeat,
                        contentDescription = "Repetir",
                        tint = Color.White.copy(alpha = 0.85f)
                    )
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
                        color = if (state.pointA != null) pageAccent else Color.White.copy(alpha = 0.85f)
                    )
                }
                TextButton(
                    onClick = { playerController.setPointB() },
                    enabled = state.pointA != null
                ) {
                    Text(
                        "B" + if (state.pointB != null) " ✓" else "",
                        color = if (state.pointB != null) pageAccent else Color.White.copy(alpha = 0.85f)
                    )
                }
                if (state.pointA != null || state.pointB != null) {
                    IconButton(onClick = { playerController.clearABRepeat() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Limpar A-B", tint = Color.White.copy(alpha = 0.85f))
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
                    Icon(Icons.Filled.BookmarkAdd, contentDescription = "Adicionar marcador", tint = Color.White.copy(alpha = 0.85f))
                }
                IconButton(onClick = { showBookmarksSheet = true }) {
                    Icon(Icons.Filled.Bookmarks, contentDescription = "Ver marcadores", tint = Color.White.copy(alpha = 0.85f))
                }
            }
        }
    }
    } // fim do MaterialTheme(pageAccent)
    } // fim do Box de fundo

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

/**
 * Disco de vinil com a capa do álbum encaixada no centro, girando enquanto
 * a música toca. Usa um [Animatable] em vez de rememberInfiniteTransition
 * porque precisamos CONGELAR a rotação exatamente onde ela parou ao
 * pausar — como um toca-discos de verdade, que não "volta" pro início, só
 * para de girar ali mesmo.
 */
@Composable
private fun VinylRecord(
    bitmap: Bitmap?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val angle = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                angle.animateTo(
                    targetValue = angle.value + 360f,
                    animationSpec = tween(durationMillis = 9000, easing = LinearEasing)
                )
            }
        }
        // Quando isPlaying vira false, este LaunchedEffect é cancelado pelo
        // próprio Compose (a key mudou) no meio da animação — angle.value
        // fica exatamente onde estava, sem precisar de nenhum código extra.
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = angle.value }
                .clip(CircleShape)
                .background(Color(0xFF141414))
                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
        ) {
            // Sulcos do vinil — círculos concêntricos bem sutis entre a
            // borda do disco e a capa no centro.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val maxRadius = size.minDimension / 2f
                val grooveCount = 9
                for (i in 1..grooveCount) {
                    val r = maxRadius * (0.68f + (i / grooveCount.toFloat()) * 0.30f)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        radius = r,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            // Capa do álbum (ou ícone de nota musical) ocupando o miolo do disco
            Box(
                modifier = Modifier
                    .fillMaxSize(0.62f)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF262626)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxSize(0.4f)
                        )
                    }
                }
            }

            // Furo do eixo, bem no centro
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color(0xFF0A0A0A))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            )
        }
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
