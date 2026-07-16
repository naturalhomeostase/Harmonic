package com.harmonic.player.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harmonic.player.data.GradientTheme
import com.harmonic.player.data.MusicDatabase
import com.harmonic.player.data.Playlist
import com.harmonic.player.data.PlaylistSongCrossRef
import com.harmonic.player.data.SettingsRepository
import com.harmonic.player.data.Song
import com.harmonic.player.playback.PlayerController
import com.harmonic.player.ui.miniplayer.MiniPlayer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class LibraryTab(val label: String) {
    SONGS("Músicas"), ARTISTS("Artistas"), ALBUMS("Álbuns"),
    GENRES("Gêneros"), FOLDERS("Pastas"), FAVORITES("Favoritas")
}

/**
 * Brush opcional pro título das músicas na lista, quando o usuário ativa
 * "gradiente nos títulos" na tela de Aparência. `null` = título com cor
 * sólida (comportamento padrão). Como [SongRow] é privado deste arquivo e
 * usado só aqui, um CompositionLocal evita ter que passar esse parâmetro
 * por todas as chamadas de SongList/SongRow espalhadas pelas abas.
 */
private val LocalSongTitleBrush = compositionLocalOf<Brush?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    database: MusicDatabase,
    playerController: PlayerController,
    settings: SettingsRepository,
    onSongClick: (List<Song>, Int) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlaylists: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val dao = remember { database.songDao() }
    val playbackState by playerController.uiState.collectAsState()

    // Gradiente do título das músicas, se o usuário ativou essa opção em
    // Aparência — reaproveita as cores do tema de gradiente ativo (ou o
    // padrão "Meia-noite" quando o fundo é uma imagem, já que aí não existe
    // uma paleta de gradiente selecionada).
    val titleGradientEnabled by settings.titleGradientEnabled.collectAsState(initial = false)
    val gradientThemeName by settings.gradientTheme.collectAsState(initial = null)
    val titleBrush = if (titleGradientEnabled) {
        val theme = GradientTheme.values().find { it.name == gradientThemeName } ?: GradientTheme.MIDNIGHT
        Brush.linearGradient(theme.colorsArgb.map { Color(it) })
    } else null

    // Música selecionada pra mostrar o menu de opções (tocar em seguida,
    // adicionar à fila, adicionar à playlist). null = menu fechado.
    var songForOptions by remember { mutableStateOf<Song?>(null) }

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

    CompositionLocalProvider(LocalSongTitleBrush provides titleBrush) {
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
                        Text("Harmonic", color = MaterialTheme.colorScheme.primary)
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
                        IconButton(onClick = onOpenPlaylists) {
                            Icon(Icons.Filled.QueueMusic, contentDescription = "Playlists")
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
                val accentColor = MaterialTheme.colorScheme.primary
                ScrollableTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    edgePadding = 16.dp,
                    // Sem o divisor padrão (linha cinza full-width) — some
                    // com a sensação de "barra escura" atrás do menu.
                    divider = {},
                    indicator = { tabPositions ->
                        if (selectedTab.ordinal < tabPositions.size) {
                            TabRowDefaults.Indicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                                height = 3.dp,
                                color = accentColor
                            )
                        }
                    }
                ) {
                    LibraryTab.values().forEach { tab ->
                        val isSelected = selectedTab == tab
                        Tab(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            text = {
                                Text(
                                    tab.label,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f),
                                    fontSize = if (isSelected) 15.sp else 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            selectedContentColor = Color.White,
                            unselectedContentColor = Color.White.copy(alpha = 0.55f)
                        )
                    }
                }
            }

            when {
                // Busca tem prioridade sobre tudo — mostra resultado direto
                searchQuery.isNotBlank() -> SongList(
                    songs = searchResults,
                    onSongClick = { onSongClick(searchResults, searchResults.indexOf(it)); onOpenNowPlaying() },
                    onFavoriteToggle = { song -> scope.launch { dao.setFavorite(song.id, !song.isFavorite) } },
                    onLongPress = { songForOptions = it },
                    currentPlayingSongId = playbackState.currentSong?.id,
                    isPlaying = playbackState.isPlaying
                )

                selectedTab == LibraryTab.SONGS -> {
                    val songs by dao.getAllSongs().collectAsState(initial = emptyList())
                    SongList(
                        songs = songs,
                        onSongClick = { onSongClick(songs, songs.indexOf(it)); onOpenNowPlaying() },
                        onFavoriteToggle = { song -> scope.launch { dao.setFavorite(song.id, !song.isFavorite) } },
                        onLongPress = { songForOptions = it },
                        currentPlayingSongId = playbackState.currentSong?.id,
                        isPlaying = playbackState.isPlaying
                    )
                }

                selectedTab == LibraryTab.FAVORITES -> {
                    val songs by dao.getFavorites().collectAsState(initial = emptyList())
                    SongList(
                        songs = songs,
                        onSongClick = { onSongClick(songs, songs.indexOf(it)); onOpenNowPlaying() },
                        onFavoriteToggle = { song -> scope.launch { dao.setFavorite(song.id, !song.isFavorite) } },
                        onLongPress = { songForOptions = it },
                        currentPlayingSongId = playbackState.currentSong?.id,
                        isPlaying = playbackState.isPlaying
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
                            onFavoriteToggle = { song -> scope.launch { dao.setFavorite(song.id, !song.isFavorite) } },
                            onLongPress = { songForOptions = it },
                            currentPlayingSongId = playbackState.currentSong?.id,
                            isPlaying = playbackState.isPlaying
                        )
                    }
                }

                selectedTab == LibraryTab.ALBUMS && drilledAlbumId == null -> {
                    val albums by dao.getAlbums().collectAsState(initial = emptyList())
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        items(albums, key = { it.albumId }) { album ->
                            ListItem(
                                headlineContent = { Text(album.album, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
                            onFavoriteToggle = { song -> scope.launch { dao.setFavorite(song.id, !song.isFavorite) } },
                            onLongPress = { songForOptions = it },
                            currentPlayingSongId = playbackState.currentSong?.id,
                            isPlaying = playbackState.isPlaying
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
                            onFavoriteToggle = { song -> scope.launch { dao.setFavorite(song.id, !song.isFavorite) } },
                            onLongPress = { songForOptions = it },
                            currentPlayingSongId = playbackState.currentSong?.id,
                            isPlaying = playbackState.isPlaying
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
                            onFavoriteToggle = { song -> scope.launch { dao.setFavorite(song.id, !song.isFavorite) } },
                            onLongPress = { songForOptions = it },
                            currentPlayingSongId = playbackState.currentSong?.id,
                            isPlaying = playbackState.isPlaying
                        )
                    }
                }
            }
        }
    }
    } // fim do CompositionLocalProvider(LocalSongTitleBrush)

    songForOptions?.let { song ->
        SongOptionsSheet(
            song = song,
            dao = dao,
            onDismiss = { songForOptions = null },
            onPlayNext = { playerController.playNext(song); songForOptions = null },
            onAddToQueueEnd = { playerController.addToQueueEnd(song); songForOptions = null }
        )
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
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GroupList(items: List<String>, onClick: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items, key = { it }) { name ->
            ListItem(
                headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onClick(name) }
            )
        }
    }
}

@Composable
private fun SongList(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onFavoriteToggle: (Song) -> Unit,
    onLongPress: (Song) -> Unit,
    currentPlayingSongId: Long? = null,
    isPlaying: Boolean = false
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(songs, key = { it.id }) { song ->
            SongRow(
                song = song,
                onClick = { onSongClick(song) },
                onFavoriteToggle = { onFavoriteToggle(song) },
                onLongPress = { onLongPress(song) },
                isCurrentlyPlaying = song.id == currentPlayingSongId,
                isPlaying = isPlaying
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SongRow(
    song: Song,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onLongPress: () -> Unit,
    isCurrentlyPlaying: Boolean = false,
    isPlaying: Boolean = false
) {
    val accentColor = MaterialTheme.colorScheme.primary
    ListItem(
        leadingContent = {
            com.harmonic.player.ui.common.AlbumArt(
                song = song,
                modifier = Modifier
                    .size(48.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            )
        },
        headlineContent = {
            val titleBrush = LocalSongTitleBrush.current
            if (titleBrush != null && !isCurrentlyPlaying) {
                // Gradiente só no título; a música tocando no momento
                // continua com a cor de destaque sólida, pra não perder o
                // "qual música está tocando agora" que o gradiente ia diluir.
                Text(
                    song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = LocalTextStyle.current.copy(brush = titleBrush)
                )
            } else {
                Text(
                    song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentlyPlaying) accentColor else Color.White
                )
            }
        },
        supportingContent = {
            Text(
                "${song.artist} • ${song.album}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrentlyPlaying) accentColor.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.7f)
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Indica visualmente qual música da lista está tocando
                // agora — sem isso, era impossível saber só olhando a lista.
                if (isCurrentlyPlaying) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Equalizer else Icons.Filled.Pause,
                        contentDescription = if (isPlaying) "Tocando agora" else "Pausado",
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = onFavoriteToggle, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favoritar",
                        tint = if (song.isFavorite) accentColor else Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
    )
}

/**
 * Bottom sheet de opções aberto com toque longo numa música: tocar em
 * seguida, adicionar ao final da fila, ou adicionar a uma playlist
 * (incluindo criar uma nova playlist na hora).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongOptionsSheet(
    song: Song,
    dao: com.harmonic.player.data.SongDao,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueueEnd: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column {
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))

            ListItem(
                leadingContent = { Icon(Icons.Filled.SkipNext, contentDescription = null) },
                headlineContent = { Text("Tocar em seguida") },
                modifier = Modifier.clickable(onClick = onPlayNext)
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.QueueMusic, contentDescription = null) },
                headlineContent = { Text("Adicionar ao final da fila") },
                modifier = Modifier.clickable(onClick = onAddToQueueEnd)
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.PlaylistAdd, contentDescription = null) },
                headlineContent = { Text("Adicionar à playlist") },
                modifier = Modifier.clickable { showPlaylistPicker = true }
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showPlaylistPicker) {
        val playlists by dao.getPlaylists().collectAsState(initial = emptyList())
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            title = { Text("Adicionar a qual playlist?") },
            text = {
                Column {
                    if (playlists.isEmpty()) {
                        Text("Nenhuma playlist ainda.")
                    }
                    playlists.forEach { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name) },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    val currentCount = dao.getPlaylistSongs(playlist.id).first().size
                                    dao.addToPlaylist(PlaylistSongCrossRef(playlist.id, song.id, currentCount))
                                }
                                showPlaylistPicker = false
                                onDismiss()
                            }
                        )
                    }
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.PlaylistAdd, contentDescription = null) },
                        headlineContent = { Text("Nova playlist...") },
                        modifier = Modifier.clickable {
                            showPlaylistPicker = false
                            showCreatePlaylistDialog = true
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistPicker = false }) { Text("Fechar") }
            }
        )
    }

    if (showCreatePlaylistDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
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
                            val newId = dao.insertPlaylist(Playlist(name = newName.trim()))
                            dao.addToPlaylist(PlaylistSongCrossRef(newId, song.id, 0))
                        }
                        showCreatePlaylistDialog = false
                        onDismiss()
                    }
                ) { Text("Criar e adicionar") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Cancelar") }
            }
        )
    }
}
