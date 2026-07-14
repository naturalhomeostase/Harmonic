package com.harmonic.player.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Antes, o escaneamento do MediaStore rodava dentro de um LaunchedEffect na
 * LibraryScreen — o que significa que toda vez que o usuário saía e voltava
 * pra essa tela (ex: abrindo "Agora Tocando"), o escaneamento rodava de novo
 * do zero. Aqui centralizamos isso: escaneia uma vez, e depois só reage a
 * mudanças reais no MediaStore, no ciclo de vida do app inteiro — não da tela.
 */
class MusicRepository(
    private val scanner: MediaStoreScanner,
    private val dao: SongDao,
    private val settings: SettingsRepository
) {
    fun startObserving(scope: CoroutineScope) {
        scope.launch { runScan() }
        scanner.observeChanges()
            .onEach { runScan() }
            .launchIn(scope)
    }

    private suspend fun runScan() {
        val ignored = settings.ignoredFolders.first()
        val songs = scanner.scan(ignoredFolders = ignored)
        val current = songs.map { it.mediaStoreId }
        val existingIds = dao.getAllMediaStoreIds()
        val removed = existingIds - current.toSet()
        if (removed.isNotEmpty()) dao.deleteByMediaStoreIds(removed.toList())
        dao.insertAll(songs)
    }
}
