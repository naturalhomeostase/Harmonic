package com.harmonic.player.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.harmonic.player.data.Playlist
import com.harmonic.player.data.SongDao
import com.harmonic.player.ui.common.ActionSheet
import com.harmonic.player.ui.common.ActionSheetItem
import com.harmonic.player.ui.common.SortMenuButton
import com.harmonic.player.ui.common.SortOption
import kotlinx.coroutines.launch

private val playlistSortOptions = listOf(
    SortOption("createdAt", "Data adicionada"),
    SortOption("modifiedAt", "Modificada")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    dao: SongDao,
    onBack: () -> Unit,
    onOpenPlaylist: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    val playlists by dao.getPlaylists().collectAsState(initial = emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistForOptions by remember { mutableStateOf<Playlist?>(null) }
    var sortKey by remember { mutableStateOf("createdAt") }
    var sortAscending by remember { mutableStateOf(false) }

    val sortedPlaylists = remember(playlists, sortKey, sortAscending) {
        // Favoritas sempre no topo, dentro do grupo aplica a ordenação escolhida.
        val base = when (sortKey) {
            "modifiedAt" -> playlists.sortedBy { it.modifiedAt }
            else -> playlists.sortedBy { it.createdAt }
        }
        val ordered = if (sortAscending) base else base.reversed()
        ordered.sortedByDescending { it.isFavorite }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Playlists", color = MaterialTheme.colorScheme.primary)
                        Text(
                            "${playlists.size} playlist(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    SortMenuButton(
                        options = playlistSortOptions,
                        selectedKey = sortKey,
                        ascending = sortAscending,
                        onSelect = { sortKey = it },
                        onToggleDirection = { sortAscending = !sortAscending }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Nova playlist")
            }
        }
    ) { padding ->
        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Nenhuma playlist ainda. Toque em + pra criar a primeira.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(sortedPlaylists, key = { it.id }) { playlist ->
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.QueueMusic, contentDescription = null) },
                        headlineContent = { Text(playlist.name) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onOpenPlaylist(playlist.id) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = {
                                    scope.launch { dao.setPlaylistFavorite(playlist.id, !playlist.isFavorite) }
                                }) {
                                    Icon(
                                        if (playlist.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        contentDescription = "Favoritar",
                                        tint = if (playlist.isFavorite) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                                    )
                                }
                                IconButton(onClick = { playlistForOptions = playlist }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções")
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Nova playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Nome da playlist") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        scope.launch {
                            dao.insertPlaylist(Playlist(name = newName.trim()))
                        }
                        showCreateDialog = false
                    }
                ) { Text("Criar") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancelar") }
            }
        )
    }

    playlistForOptions?.let { playlist ->
        var showRenameDialog by remember { mutableStateOf(false) }
        var showDeleteConfirm by remember { mutableStateOf(false) }
        var showSheet by remember { mutableStateOf(true) }

        if (showSheet) {
            ActionSheet(
                onDismiss = { showSheet = false; playlistForOptions = null },
                title = playlist.name,
                items = listOf(
                    ActionSheetItem(Icons.Filled.Edit, "Renomear") { showSheet = false; showRenameDialog = true },
                    ActionSheetItem(
                        Icons.Filled.Delete, "Excluir",
                        tint = MaterialTheme.colorScheme.error
                    ) { showSheet = false; showDeleteConfirm = true }
                )
            )
        }

        if (showRenameDialog) {
            var newName by remember { mutableStateOf(playlist.name) }
            AlertDialog(
                onDismissRequest = { showRenameDialog = false; playlistForOptions = null },
                title = { Text("Renomear playlist") },
                text = {
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true)
                },
                confirmButton = {
                    TextButton(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            scope.launch { dao.renamePlaylist(playlist.id, newName.trim()) }
                            showRenameDialog = false
                            playlistForOptions = null
                        }
                    ) { Text("Salvar") }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false; playlistForOptions = null }) { Text("Cancelar") }
                }
            )
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false; playlistForOptions = null },
                title = { Text("Excluir playlist?") },
                text = { Text("\"${playlist.name}\" será excluída. As músicas continuam na sua biblioteca.") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch { dao.deletePlaylist(playlist.id) }
                        showDeleteConfirm = false
                        playlistForOptions = null
                    }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false; playlistForOptions = null }) { Text("Cancelar") }
                }
            )
        }
    }
}
