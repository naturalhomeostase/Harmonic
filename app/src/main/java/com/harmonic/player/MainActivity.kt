package com.harmonic.player

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.harmonic.player.playback.EqualizerController
import com.harmonic.player.playback.PlayerController
import com.harmonic.player.ui.common.AppBackground
import com.harmonic.player.ui.equalizer.EqualizerScreen
import com.harmonic.player.ui.library.LibraryScreen
import com.harmonic.player.ui.nowplaying.NowPlayingScreen
import com.harmonic.player.ui.playlists.PlaylistDetailScreen
import com.harmonic.player.ui.playlists.PlaylistsScreen
import com.harmonic.player.ui.settings.AppearanceScreen
import com.harmonic.player.ui.theme.HarmonicTheme
import com.harmonic.player.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    private lateinit var playerController: PlayerController

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as HarmonicApp

        playerController = PlayerController(applicationContext, app.database.songDao(), app.settings)
        playerController.connect()

        setContent {
            val accentColorArgb by app.settings.accentColor.collectAsState(initial = null)
            val themeModeStr by app.settings.themeMode.collectAsState(initial = "dark")

            val themeMode = when (themeModeStr) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                "amoled" -> ThemeMode.AMOLED
                else -> ThemeMode.SYSTEM
            }
            val customAccent = accentColorArgb?.let { Color(it) }
            val scope = rememberCoroutineScope()

            HarmonicTheme(themeMode = themeMode, customAccentColor = customAccent) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                    AppBackground(settings = app.settings) {
                        // Pedimos as duas permissões juntas: acesso ao áudio
                        // (essencial pra biblioteca funcionar) e notificações
                        // (essencial no Android 13+ pra aparecer o player na
                        // barra de notificação — sem essa permissão, o
                        // MediaSessionService cria a notificação mas o
                        // sistema simplesmente não mostra ela).
                        val permissions = buildList {
                            add(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                    Manifest.permission.READ_MEDIA_AUDIO
                                else Manifest.permission.READ_EXTERNAL_STORAGE
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        val permissionsState = rememberMultiplePermissionsState(permissions)

                        val audioPermissionGranted = permissionsState.permissions.any {
                            (it.permission == Manifest.permission.READ_MEDIA_AUDIO ||
                             it.permission == Manifest.permission.READ_EXTERNAL_STORAGE) && it.status.isGranted
                        }

                        LaunchedEffect(Unit) {
                            if (!audioPermissionGranted) {
                                permissionsState.launchMultiplePermissionRequest()
                            }
                        }

                        // Assim que a permissão de áudio for concedida (seja
                        // porque já estava concedida, seja porque o usuário
                        // acabou de aceitar), força um novo escaneamento.
                        // Sem isso, a primeira leitura (que roda no
                        // Application.onCreate, antes da permissão existir)
                        // não encontra nada, e só um reinício completo do
                        // app rodava o scan de novo já com permissão.
                        LaunchedEffect(audioPermissionGranted) {
                            if (audioPermissionGranted) {
                                app.musicRepository.rescanNow(scope)
                            }
                        }

                        if (audioPermissionGranted) {
                            HarmonicNavHost(playerController, app)
                        } else {
                            com.harmonic.player.ui.common.PermissionRationaleScreen(
                                onRequestPermission = { permissionsState.launchMultiplePermissionRequest() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        playerController.persistNow()
        super.onStop()
    }

    override fun onDestroy() {
        playerController.release()
        super.onDestroy()
    }
}

@Composable
private fun HarmonicNavHost(playerController: PlayerController, app: HarmonicApp) {
    val navController = rememberNavController()

    // Uma única instância do equalizador vive durante toda a navegação —
    // não só enquanto a tela dele está aberta. Senão, o efeito sonoro
    // desapareceria assim que o usuário voltasse pra Biblioteca.
    val equalizerController = remember { EqualizerController() }
    val audioSessionId by com.harmonic.player.playback.PlaybackAudioSession.sessionId.collectAsState()

    // Carrega os valores salvos do equalizador uma única vez, assim que o
    // app abre (antes mesmo de o usuário visitar a tela do equalizador).
    LaunchedEffect(Unit) {
        equalizerController.restoreState(
            enabled = app.settings.eqEnabled.first(),
            bandLevels = app.settings.eqBandLevels.first(),
            bassBoost = app.settings.bassBoostStrength.first(),
            virtualizer = app.settings.virtualizerStrength.first(),
            reverbPreset = app.settings.reverbPreset.first()
        )
    }

    // Reconecta os efeitos sempre que o audioSessionId do ExoPlayer mudar
    // (acontece ao iniciar a reprodução pela primeira vez, por exemplo).
    LaunchedEffect(audioSessionId) {
        if (audioSessionId != 0) {
            equalizerController.attach(audioSessionId)
        }
    }

    NavHost(navController = navController, startDestination = "library") {
        composable("library") {
            LibraryScreen(
                database = app.database,
                playerController = playerController,
                settings = app.settings,
                onSongClick = { queue, index -> playerController.playQueue(queue, index) },
                onOpenNowPlaying = { navController.navigate("now_playing") },
                onOpenSettings = { navController.navigate("settings") },
                onOpenPlaylists = { navController.navigate("playlists") }
            )
        }
        composable("now_playing") {
            NowPlayingScreen(
                playerController = playerController,
                dao = app.database.songDao(),
                onBack = { navController.popBackStack() },
                onOpenEqualizer = { navController.navigate("equalizer") }
            )
        }
        composable("settings") {
            com.harmonic.player.ui.settings.SettingsScreen(
                settings = app.settings,
                onBack = { navController.popBackStack() },
                onOpenTheme = { navController.navigate("appearance") },
                onOpenHiddenFolders = { navController.navigate("hidden_folders") }
            )
        }
        composable("hidden_folders") {
            com.harmonic.player.ui.settings.HiddenFoldersScreen(
                database = app.database,
                onBack = { navController.popBackStack() }
            )
        }
        composable("appearance") {
            AppearanceScreen(
                settings = app.settings,
                onBack = { navController.popBackStack() }
            )
        }
        composable("equalizer") {
            EqualizerScreen(
                equalizerController = equalizerController,
                settings = app.settings,
                onBack = { navController.popBackStack() }
            )
        }
        composable("playlists") {
            PlaylistsScreen(
                dao = app.database.songDao(),
                onBack = { navController.popBackStack() },
                onOpenPlaylist = { id -> navController.navigate("playlist/$id") }
            )
        }
        composable(
            route = "playlist/{playlistId}",
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: return@composable
            PlaylistDetailScreen(
                playlistId = playlistId,
                dao = app.database.songDao(),
                context = app.applicationContext,
                onBack = { navController.popBackStack() },
                onPlaySongs = { songs, index -> playerController.playQueue(songs, index); navController.navigate("now_playing") }
            )
        }
    }
}
