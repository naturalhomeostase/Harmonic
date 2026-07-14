package com.harmonic.player.ui.nowplaying

import androidx.compose.foundation.background
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
import com.harmonic.player.playback.PlayerController
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen(playerController: PlayerController, onBack: () -> Unit) {
    val state by playerController.uiState.collectAsState()
    var sliderPosition by remember { mutableStateOf(0f) }
    var isUserSeeking by remember { mutableStateOf(false) }

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
            // Capa do álbum (placeholder — Coil vai carregar a URI real do MediaStore)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            Spacer(Modifier.height(24.dp))

            Text(
                state.currentSong?.title ?: "Nada tocando",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                state.currentSong?.artist ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
        }
    }
}

/** Formata milissegundos como "m:ss", ex: 1234000ms -> "20:34". */
private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
