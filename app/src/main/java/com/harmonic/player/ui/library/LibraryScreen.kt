package com.harmonic.player.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harmonic.player.data.MusicDatabase
import com.harmonic.player.data.Song
import com.harmonic.player.playback.PlayerController
import com.harmonic.player.ui.miniplayer.MiniPlayer
import kotlinx.coroutines.launch

private enum class LibraryTab(val label: String) {
    SONGS("Músicas"), ARTISTS("Artistas"), ALBUMS("Álbuns"),
    GENRES("Gêneros"), FOLDERS("Pastas"), FAVORITES("Favoritas")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    database: MusicDatabase,
    playerController: PlayerController,
    onSongClick: (List<Song>, Int) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val dao = remember { database.songDao() }
    val playbackState by playerController.uiState.collectAsState()

    var selectedTab by remember { mutableStateOf(LibraryTab.SONGS) }
    // Quando o usuário toca num nome de artista/álbum/gênero/pasta, guardamos
    // aqui qual grupo foi escolhido, pra mostrar as músicas daquele grupo.
    // Voltar (seta ou botão físico) limpa isso e volta pra lista de grupos.
    var drilledGroup by remember { mutableStateOf<String?>(null) }
    var drilledAlbumId by remember { mutableStateOf<Long?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    // Volta pra lista de grupos ao trocar de aba
    LaunchedEffect(selectedTab) {
        drilledGroup = null
        drilledAlbumId = null
    }

    // Pede foco assim que o campo de busca aparece, para o usuário poder
    // digitar direto sem precisar tocar duas vezes.
    LaunchedEffect(isSearching) {
        if (isSearching) searchFocusRequester.requestFocus()
    }

    androidx.activity.compose.BackHandler(enabled = drilledGroup != null || drilledAlbumId != null) {
        drilledGroup = null
        drilledAlbumId = null
    }

    val searchResults by (if (searchQuery.isNotBlank()) dao.search(searchQuery) else dao.getAllSongs())
        .collectAsState(initial = emptyList())

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar músicas, artistas, álbuns...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester)
                        )
                    } else {
                        Text("Harmonic")
                    }
                },
                navigationIcon = {
                    if (isSearching) {
                        IconButton(onClick = { isSearching = false; searchQuery = "" }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Fechar busca")
                        }
                    }
                },
                actions = {
                    if (!isSearching) {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Buscar")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Aparência")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            MiniPlayer(
                state = playbackState,
                onTogglePlayPause = { playerController.togglePlayPause() },
                onSkipNext = { playerController.skipNext() },
                onOpenNowPlaying = onOpenNowPlaying
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            if (searchQuery.isBlank()) {
                ScrollableTabRow(selectedTabIndex = selectedTab.ordinal) {
                    LibraryTab.values().forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(tab.label) }
                        )
                    }
                }
            }

            when {
                // Busca tem prioridade sobre tudo — mostra resultado direto
                searchQuery.isNotBlank() -> SongList(
                    songs = searchResults,
                    onSongClick = { onSongClick(searchResults, searchResults.indexOf(it)); onOpenNowPlaying() },
                    onFavoriteToggle = { song -> scope.launch { dao.setFavorite(song.id, !song.isFavorite) } }
                )

                selectedTab == LibraryTab.SONGS -> {
                    val songs by dao.getAllSongs().collectAsState(initial = emptyList())
                    SongList(
                        songs = songs,
                        onSongClick = { onSongClick(songs, songs.indexOf(it)); onOpenNowPlaying() },
                        onFavoriteToggle = { song -> scope.launch { dao.setFavorite(song.id, !song.isFavorite) } }
                    )
                }

                selectedTab == LibraryTab.FAVORITES -> {
                    val songs by dao.getFavorites().collectAsState(initial = emptyList())
                    SongList(
                        songs = songs,
                        onSongClick = { onSongClick(songs, songs.indexOf(it)); onOpenNowPlaying() },
                        onFavoriteToggle = { song -> scope.launch { dao.setFavorite(song.id, !song.isFavorite) } }
                    )
                }

                selectedTab == LibraryTab.ARTISTS && drilledGroup == null -> {
                    val artists by dao.getArtists().collectAsState(initial = emptyList())
                    GroupList(items = artists) { drilledGroup = it }
                }
                selectedTab == LibraryTab.ARTISTS -> {
                    val songs by dao.getSongsByArtist(drilledGroup!!).collectAsState(initial = emptyList())
                    Column {
                        GroupHeader(title = drilledGroup!!) { drilledGroup = null }
                        SongList(
                            songs = songs,
                            onSongClick = { onSongClick(songs, songs.indexOf(it)); onOpenNowPlaying() },
                            onFavoriteToggle = { song -> scope.launch { dao.setFavorite(song.id, !song.isFavorite) } }
                        )
                    }
                }

                selectedTab == LibraryTab.ALBUMS && drilledAlbumId == null -> {
                    val albums by dao.getAlbums().collectAsState(initial = emptyList())
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        items(albums, key = { it.albumId }) { album ->
                            ListItem(
                                headlineContent = { Text(album.album, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.clickable {
                                    drilledGroup = album.album
                                    drilledAlbumId = album.albumId
                                }
                            )
                        }
                    }
                }
                selectedTab == LibraryTab.ALBUMS -> {
                    val songs by dao.getSongsByAlbum(drilledAlbumId!!).collectAsState(initial = emptyList())
                    Column {
                        GroupHeader(title = drilledGroup ?: "") { drilledGroup = null; drilledAlbumId = null }
                        SongList(
                            songs = songs,
                            onSongClick = { onSongClick(songs, songs.indexOf(it)); onOpenNowPlaying() },
                            onFavoriteToggle = { song -> scope.launch { dao.setFavorite(song.id, !song.isFavorite) } }
                        )
                    }
                }

                selectedTab == LibraryTab.GENRES && drilledGroup == null -> {
                    val genres by dao.getGenres().collectAsState(initial = emptyList())
                    GroupList(items = genres) { drilledGroup = it }
                }
                selectedTab == LibraryTab.GENRES -> {
                    val songs by dao.getSongsByGenre(drilledGroup!!).collectAsState(initial = emptyList())
                    Column {
                        GroupHeader(title = drilledGroup!!) { drilledGroup = null }
                        SongList(
                            songs = songs,
                            onSongClick = { onSongClick(songs, songs.indexOf(it)); onOpenNowPlaying() },
                            onFavoriteToggle = { song -> scope.launch { dao.setFavorite(song.id, !song.isFavorite) } }
                        )
                    }
                }

                selectedTab == LibraryTab.FOLDERS && drilledGroup == null -> {
                    val folders by dao.getFolders().collectAsState(initial = emptyList())
                    GroupList(items = folders) { drilledGroup = it }
                }
                selectedTab == LibraryTab.FOLDERS -> {
                    val songs by dao.getSongsByFolder(drilledGroup!!).collectAsState(initial = emptyList())
                    Column {
                        GroupHeader(title = drilledGroup!!.substringAfterLast('/')) { drilledGroup = null }
                        SongList(
                            songs = songs,
                            onSongClick = { onSongClick(songs, songs.indexOf(it)); onOpenNowPlaying() },
                            onFavoriteToggle = { song -> scope.launch { dao.setFavorite(song.id, !song.isFavorite) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
        }
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GroupList(items: List<String>, onClick: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items, key = { it }) { name ->
            ListItem(
                headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.clickable { onClick(name) }
            )
        }
    }
}

@Composable
private fun SongList(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onFavoriteToggle: (Song) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(songs, key = { it.id }) { song ->
            SongRow(
                song = song,
                onClick = { onSongClick(song) },
                onFavoriteToggle = { onFavoriteToggle(song) }
            )
        }
    }
}

@Composable
private fun SongRow(song: Song, onClick: () -> Unit, onFavoriteToggle: () -> Unit) {
    ListItem(
        headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("${song.artist} • ${song.album}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            IconButton(onClick = onFavoriteToggle, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favoritar"
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}
