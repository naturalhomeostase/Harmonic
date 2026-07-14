package com.harmonic.player.ui.playlists

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harmonic.player.data.PlaylistImportExport
import com.harmonic.player.data.PlaylistSongCrossRef
import com.harmonic.player.data.Song
import com.harmonic.player.data.SongDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    dao: SongDao,
    context: Context,
    onBack: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val songs by dao.getPlaylistSongs(playlistId).collectAsState(initial = emptyList())
    val playlists by dao.getPlaylists().collectAsState(initial = emptyList())
    val playlistName = playlists.find { it.id == playlistId }?.name ?: "Playlist"

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
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(playlistName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddSongsDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Adicionar músicas")
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
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
        if (songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Playlist vazia. Toque em + pra adicionar músicas.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(songs, key = { it.id }) { song ->
                    ListItem(
                        headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = {
                            IconButton(onClick = {
                                scope.launch { dao.removeFromPlaylist(playlistId, song.id) }
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remover da playlist")
                            }
                        },
                        modifier = Modifier.clickable { onPlaySongs(songs, songs.indexOf(song)) }
                    )
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
