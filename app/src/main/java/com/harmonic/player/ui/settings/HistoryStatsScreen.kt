package com.harmonic.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harmonic.player.data.MusicDatabase
import com.harmonic.player.data.Song
import com.harmonic.player.playback.PlayerController

/**
 * Resumo geral da biblioteca + duas listas que já existiam prontas no
 * banco (playCount/lastPlayedAt, alimentadas de verdade agora que
 * dao.registerPlay() está conectado) mas nunca tinham uma tela própria —
 * só apareciam como opção de ordenar dentro de Músicas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryStatsScreen(database: MusicDatabase, playerController: PlayerController, onBack: () -> Unit) {
    val dao = database.songDao()
    val mostPlayed by dao.getMostPlayed().collectAsState(initial = emptyList())
    val recentlyPlayed by dao.getRecentlyPlayed().collectAsState(initial = emptyList())
    var allSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    LaunchedEffect(Unit) { allSongs = dao.getAllSongsOnce() }

    val totalPlays = allSongs.sumOf { it.playCount }
    val totalListenedMs = allSongs.sumOf { it.playCount.toLong() * it.durationMs }
    val totalHours = totalListenedMs / 3_600_000f

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Histórico e estatísticas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 12.dp)) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatBlock(value = totalPlays.toString(), label = "reproduções")
                    StatBlock(value = "%.1fh".format(totalHours), label = "tempo ouvido*")
                    StatBlock(value = allSongs.count { it.playCount > 0 }.toString(), label = "músicas diferentes")
                }
                Text(
                    "*Estimado (reproduções × duração da música) — não é o tempo exato assistido, já que uma música pode ser pulada antes do fim.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (mostPlayed.isNotEmpty()) {
                item {
                    Text(
                        "Mais tocadas",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                    )
                }
                items(mostPlayed, key = { "most_${it.id}" }) { song ->
                    HistorySongRow(song, subtitle = "${song.playCount}x") {
                        playerController.requestPlaySingleSongWithContext(mostPlayed, mostPlayed.indexOf(song), "history_most_played", "Mais tocadas")
                    }
                }
            }

            if (recentlyPlayed.isNotEmpty()) {
                item {
                    Text(
                        "Tocadas recentemente",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
                    )
                }
                items(recentlyPlayed, key = { "recent_${it.id}" }) { song ->
                    HistorySongRow(song, subtitle = song.artist) {
                        playerController.requestPlaySingleSongWithContext(recentlyPlayed, recentlyPlayed.indexOf(song), "history_recent", "Tocadas recentemente")
                    }
                }
            }

            if (mostPlayed.isEmpty() && recentlyPlayed.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        Text("Ainda não há histórico — toque em algumas músicas primeiro.", color = Color.White.copy(alpha = 0.5f))
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun StatBlock(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Text(label, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HistorySongRow(song: Song, subtitle: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            com.harmonic.player.ui.common.AlbumArt(
                song = song,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
            )
        },
        headlineContent = {
            Text(song.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(subtitle, color = Color.White.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    )
}
