package com.harmonic.player.ui.playlists

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import com.harmonic.player.data.PlaylistImportExport
import com.harmonic.player.data.PlaylistSongCrossRef
import com.harmonic.player.data.Song
import com.harmonic.player.data.SongDao
import com.harmonic.player.playback.PlayerController
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    dao: SongDao,
    context: Context,
    playerController: PlayerController,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val songs by dao.getPlaylistSongs(playlistId).collectAsState(initial = emptyList())
    val playlists by dao.getPlaylists().collectAsState(initial = emptyList())
    val playlistName = playlists.find { it.id == playlistId }?.name ?: "Playlist"
    val playbackState by playerController.uiState.collectAsState()
    val sourceKey = "playlist:$playlistId"
    val isThisPlaylistActive = playbackState.sourceKey == sourceKey

    var showAddSongsDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val paths = PlaylistImportExport.parseM3U(context, uri)
                val allSongs = dao.getAllSongs().first()
                val matched = allSongs.filter { it.path in paths }
                var position = songs.size
                matched.forEach { song ->
                    dao.addToPlaylist(PlaylistSongCrossRef(playlistId, song.id, position))
                    position++
                }
                dao.touchPlaylist(playlistId)
            }
        }
    }

    fun play(list: List<Song>, index: Int) {
        playerController.requestPlayQueue(list, index, sourceKey, playlistName)
        onOpenNowPlaying()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(playlistName, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddSongsDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Adicionar músicas", tint = Color.White)
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções", tint = Color.White)
                    }
                    com.harmonic.player.ui.common.ThemedDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Exportar como M3U") },
                            onClick = {
                                menuExpanded = false
                                val uri = PlaylistImportExport.exportToM3U(context, playlistName, songs)
                                PlaylistImportExport.shareM3U(context, uri, playlistName)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Importar de M3U") },
                            onClick = {
                                menuExpanded = false
                                importLauncher.launch("audio/*")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Excluir playlist") },
                            onClick = {
                                menuExpanded = false
                                scope.launch { dao.deletePlaylist(playlistId) }
                                onBack()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (songs.isNotEmpty()) {
                // Tocar/shuffle/repetir tudo — o "repetir" aqui só faz
                // sentido enquanto a PLAYLIST é o que está tocando; se o
                // usuário trocar pra outro álbum/artista, o repetir dessa
                // playlist não se aplica mais (é resetado, com aviso).
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { play(songs, 0) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tocar")
                    }
                    IconButton(onClick = {
                        playerController.requestPlayQueue(songs.shuffled(), 0, sourceKey, playlistName)
                        onOpenNowPlaying()
                    }) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isThisPlaylistActive && playbackState.shuffleEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f)
                        )
                    }
                    IconButton(onClick = {
                        if (!isThisPlaylistActive) {
                            // Ainda não é essa playlist que tá tocando —
                            // começa a tocar ela primeiro, já com repetir ligado.
                            playerController.requestPlayQueue(songs, 0, sourceKey, playlistName)
                            playerController.cycleRepeatMode()
                        } else {
                            playerController.cycleRepeatMode()
                        }
                    }) {
                        Icon(
                            if (isThisPlaylistActive && playbackState.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            contentDescription = "Repetir playlist",
                            tint = if (isThisPlaylistActive && playbackState.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            if (songs.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Playlist vazia. Toque em + pra adicionar músicas.", color = Color.White.copy(alpha = 0.6f))
                }
            } else {
                // Lista arrastável: segure o ícone de arrastar (⠿) e mova
                // pra cima/baixo pra reordenar. Não é LazyColumn de
                // propósito — com a virtualização da lazy, itens saindo de
                // tela durante o arraste complicam bastante o cálculo de
                // posição; como playlists raramente têm milhares de
                // músicas, uma Column simples com scroll já basta.
                var localOrder by remember(songs) { mutableStateOf(songs) }
                var draggingIndex by remember { mutableStateOf(-1) }
                var dragOffsetY by remember { mutableStateOf(0f) }
                val density = androidx.compose.ui.platform.LocalDensity.current
                val rowHeightPx = with(density) { 64.dp.toPx() }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    localOrder.forEachIndexed { index, song ->
                        val isDragging = index == draggingIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .graphicsLayer { translationY = if (isDragging) dragOffsetY else 0f }
                                .zIndex(if (isDragging) 1f else 0f)
                                .background(if (isDragging) Color.White.copy(alpha = 0.06f) else Color.Transparent)
                                .clickable { play(localOrder, index) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.DragHandle,
                                contentDescription = "Arrastar pra reordenar",
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .pointerInput(song.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggingIndex = localOrder.indexOf(song)
                                                dragOffsetY = 0f
                                            },
                                            onDragEnd = {
                                                draggingIndex = -1
                                                dragOffsetY = 0f
                                                scope.launch { dao.updatePlaylistOrder(playlistId, localOrder.map { it.id }) }
                                            },
                                            onDragCancel = { draggingIndex = -1; dragOffsetY = 0f }
                                        ) { change, delta ->
                                            change.consume()
                                            dragOffsetY += delta.y
                                            val current = draggingIndex
                                            if (current == -1) return@detectDragGesturesAfterLongPress
                                            val steps = (dragOffsetY / rowHeightPx).roundToInt()
                                            val targetIndex = (current + steps).coerceIn(0, localOrder.lastIndex)
                                            if (targetIndex != current) {
                                                val mutable = localOrder.toMutableList()
                                                val moved = mutable.removeAt(current)
                                                mutable.add(targetIndex, moved)
                                                localOrder = mutable
                                                dragOffsetY -= (targetIndex - current) * rowHeightPx
                                                draggingIndex = targetIndex
                                            }
                                        }
                                    }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                                Text(
                                    song.artist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color.White.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = {
                                scope.launch { dao.removeFromPlaylist(playlistId, song.id) }
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remover da playlist", tint = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSongsDialog) {
        AddSongsDialog(
            dao = dao,
            alreadyInPlaylist = songs.map { it.id }.toSet(),
            onDismiss = { showAddSongsDialog = false },
            onConfirm = { selected ->
                scope.launch {
                    var position = songs.size
                    selected.forEach { song ->
                        dao.addToPlaylist(PlaylistSongCrossRef(playlistId, song.id, position))
                        position++
                    }
                    dao.touchPlaylist(playlistId)
                }
                showAddSongsDialog = false
            }
        )
    }
}

@Composable
private fun AddSongsDialog(
    dao: SongDao,
    alreadyInPlaylist: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (List<Song>) -> Unit
) {
    val allSongs by dao.getAllSongs().collectAsState(initial = emptyList())
    val selected = remember { mutableStateListOf<Song>() }
    var query by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar músicas") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                val filtered = allSongs.filter {
                    it.id !in alreadyInPlaylist &&
                        (query.isBlank() || it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true))
                }
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(filtered, key = { it.id }) { song ->
                        val isSelected = song in selected
                        ListItem(
                            headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingContent = {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked) selected.add(song) else selected.remove(song)
                                    }
                                )
                            },
                            modifier = Modifier.clickable {
                                if (isSelected) selected.remove(song) else selected.add(song)
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selected.isNotEmpty(), onClick = { onConfirm(selected.toList()) }) {
                Text("Adicionar (${selected.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
