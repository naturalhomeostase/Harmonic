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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.harmonic.player.playback.PlayerController
import com.harmonic.player.ui.common.AppBackground
import com.harmonic.player.ui.library.LibraryScreen
import com.harmonic.player.ui.nowplaying.NowPlayingScreen
import com.harmonic.player.ui.settings.AppearanceScreen
import com.harmonic.player.ui.theme.HarmonicTheme
import com.harmonic.player.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {

    private lateinit var playerController: PlayerController

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        playerController = PlayerController(applicationContext)
        playerController.connect()

        val app = application as HarmonicApp

        setContent {
            val accentColorArgb by app.settings.accentColor.collectAsState(initial = null)
            val themeModeStr by app.settings.themeMode.collectAsState(initial = "system")

            val themeMode = when (themeModeStr) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                "amoled" -> ThemeMode.AMOLED
                else -> ThemeMode.SYSTEM
            }
            // Aqui é onde a cor escolhida na tela de Aparência realmente
            // chega até o tema visual do app inteiro.
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

    override fun onDestroy() {
        playerController.release()
        super.onDestroy()
    }
}

@Composable
private fun HarmonicNavHost(playerController: PlayerController, app: HarmonicApp) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "library") {
        composable("library") {
            LibraryScreen(
                database = app.database,
                playerController = playerController,
                onSongClick = { queue, index -> playerController.playQueue(queue, index) },
                onOpenNowPlaying = { navController.navigate("now_playing") },
                onOpenSettings = { navController.navigate("appearance") }
            )
        }
        composable("now_playing") {
            NowPlayingScreen(
                playerController = playerController,
                onBack = { navController.popBackStack() }
            )
        }
        composable("appearance") {
            AppearanceScreen(
                settings = app.settings,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
