package com.harmonic.player.ui.miniplayer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harmonic.player.playback.PlaybackUiState

/**
 * Barra fixa com a música atual — fica visível em qualquer lugar da
 * Biblioteca, permitindo pausar/pular sem precisar abrir "Agora Tocando".
 * Toque em qualquer área fora dos botões abre a tela cheia.
 *
 * Retorna null (não desenha nada) quando não há música tocando, para não
 * ocupar espaço à toa na tela.
 */
@Composable
fun MiniPlayer(
    state: PlaybackUiState,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onOpenNowPlaying: () -> Unit
) {
    val song = state.currentSong ?: return

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenNowPlaying)
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Capa real do álbum — cai no ícone de nota musical quando não há capa embutida.
            com.harmonic.player.ui.common.AlbumArt(
                song = song,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    song.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Botões com no mínimo 48dp de área de toque (tamanho recomendado
            // pelo Material Design), mesmo que o ícone visual seja menor
            IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pausar" else "Tocar"
                )
            }
            IconButton(onClick = onSkipNext, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Próxima")
            }
        }
    }
}
