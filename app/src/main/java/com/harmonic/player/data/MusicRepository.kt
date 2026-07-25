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
    private var observingStarted = false

    fun startObserving(scope: CoroutineScope) {
        if (observingStarted) return
        observingStarted = true
        scope.launch { runScan() }
        scanner.observeChanges()
            .onEach { runScan() }
            .launchIn(scope)
    }

    /**
     * Força um novo escaneamento imediatamente — usado logo depois que o
     * usuário concede a permissão de áudio pela primeira vez. Sem isso, o
     * escaneamento inicial (que roda no Application.onCreate, antes da
     * permissão existir) simplesmente não encontrava nada, e só um
     * reinício completo do app rodava o scan de novo com a permissão já
     * concedida — daí a sensação de "preciso reiniciar pra ver as músicas".
     */
    fun rescanNow(scope: CoroutineScope) {
        scope.launch { runScan() }
    }

    private suspend fun runScan() {
        val ignored = settings.ignoredFolders.first()
        val scanned = scanner.scan(ignoredFolders = ignored)
        if (scanned.isEmpty()) return // provavelmente sem permissão ainda; não apaga nada do banco

        // Mescla com o que já existe: preserva favoritos, contagem de
        // reproduções, última vez tocada e posição salva — sem isso, cada
        // re-scan "resetaria" essas informações mesmo a música sendo a
        // mesma (só o `REPLACE` do SQLite recriando a linha do zero).
        val existingByMediaStoreId = dao.getAllSongsOnce().associateBy { it.mediaStoreId }
        val merged = scanned.map { fresh ->
            val existing = existingByMediaStoreId[fresh.mediaStoreId]
            if (existing != null) {
                fresh.copy(
                    id = existing.id,
                    // O MediaStore só reflete as tags editadas pelo app
                    // depois de um rescan do sistema — até lá, ele ainda
                    // tem os valores antigos em cache. Preservando esses
                    // campos a partir do banco (uma vez que a música
                    // existe nele, ele vira a fonte da verdade, igual já
                    // acontecia só com o título), a edição de tags não é
                    // mais desfeita no próximo escaneamento automático.
                    title = existing.title,
                    artist = existing.artist,
                    album = existing.album,
                    genre = existing.genre,
                    trackNumber = existing.trackNumber,
                    isFavorite = existing.isFavorite,
                    playCount = existing.playCount,
                    lastPlayedAt = existing.lastPlayedAt,
                    playbackPositionMs = existing.playbackPositionMs,
                    isHidden = existing.isHidden,
                    customCoverUri = existing.customCoverUri,
                    trimStartMs = existing.trimStartMs,
                    trimEndMs = existing.trimEndMs
                )
            } else fresh
        }

        val currentIds = merged.map { it.mediaStoreId }.toSet()
        val removed = existingByMediaStoreId.keys - currentIds
        if (removed.isNotEmpty()) dao.deleteByMediaStoreIds(removed.toList())

        dao.insertAll(merged)
    }
}
