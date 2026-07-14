package com.harmonic.player

import android.app.Application
import com.harmonic.player.data.MediaStoreScanner
import com.harmonic.player.data.MusicDatabase
import com.harmonic.player.data.MusicRepository
import com.harmonic.player.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class HarmonicApp : Application() {
    // Escopo de corrotina que vive enquanto o app existir — o escaneamento
    // do MediaStore roda aqui, não dentro de uma tela, então não reinicia
    // toda vez que o usuário navega entre telas.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { MusicDatabase.getInstance(this) }
    val settings by lazy { SettingsRepository(this) }
    private val scanner by lazy { MediaStoreScanner(this) }
    val musicRepository by lazy { MusicRepository(scanner, database.songDao(), settings) }

    override fun onCreate() {
        super.onCreate()
        musicRepository.startObserving(appScope)
    }
}
