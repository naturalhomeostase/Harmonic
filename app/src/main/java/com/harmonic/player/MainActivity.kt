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
import com.google.accompanist.permissions.rememberPermissionState
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
            val themeModeStr by app.settings.themeMode.collectAsState(initial = "system")

            val themeMode = when (themeModeStr) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                "amoled" -> ThemeMode.AMOLED
                else -> ThemeMode.SYSTEM
            }
            val customAccent = accentColorArgb?.let { Color(it) }

            HarmonicTheme(themeMode = themeMode, customAccentColor = customAccent) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                    AppBackground(settings = app.settings) {
                        val audioPermission = rememberPermissionState(
                            permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                Manifest.permission.READ_MEDIA_AUDIO
                            else Manifest.permission.READ_EXTERNAL_STORAGE
                        )

                        LaunchedEffect(Unit) {
                            if (!audioPermission.status.isGranted) {
                                audioPermission.launchPermissionRequest()
                            }
                        }

                        if (audioPermission.status.isGranted) {
                            HarmonicNavHost(playerController, app)
                        } else {
                            com.harmonic.player.ui.common.PermissionRationaleScreen(
                                onRequestPermission = { audioPermission.launchPermissionRequest() }
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
                onSongClick = { queue, index -> playerController.playQueue(queue, index) },
                onOpenNowPlaying = { navController.navigate("now_playing") },
                onOpenSettings = { navController.navigate("appearance") },
                onOpenPlaylists = { navController.navigate("playlists") }
            )
        }
        composable("now_playing") {
            NowPlayingScreen(
                playerController = playerController,
                onBack = { navController.popBackStack() },
                onOpenEqualizer = { navController.navigate("equalizer") }
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
