package com.harmonic.player.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.harmonic.player.data.SettingsRepository
import com.harmonic.player.data.Song
import com.harmonic.player.data.SongDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    // Sleep timer: null = desativado, -1 = "parar no fim da música atual"
    val sleepTimerEndAt: Long? = null,
    val sleepTimerRemainingMs: Long = 0
)

/**
 * Envolve o MediaController do Media3 numa API simples de usar a partir do
 * Compose, e cuida de salvar/restaurar a fila de reprodução entre sessões
 * do app (via [SettingsRepository]) e de resolver a música atual a partir
 * do [SongDao], sem depender dos metadados (com perda) do MediaItem.
 */
class PlayerController(
    private val context: Context,
    private val dao: SongDao,
    private val settings: SettingsRepository
) {

    private var controller: MediaController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sleepTimerJob: Job? = null
    private var positionSaveJob: Job? = null

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    fun connect(onConnected: () -> Unit = {}) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            controller = controllerFuture.get()
            attachListener()
            // Se o PlaybackService já restaurou uma fila salva (ver
            // restoreSavedQueueIfNeeded nele), aqui só precisamos reconstruir
            // a lista de Song correspondente pra UI mostrar corretamente.
            resolveQueueFromControllerIfNeeded()
            startPeriodicPositionSave()
            onConnected()
        }, MoreExecutors.directExecutor())
    }

    private fun resolveQueueFromControllerIfNeeded() {
        val c = controller ?: return
        if (c.mediaItemCount == 0 || _uiState.value.queue.isNotEmpty()) return
        scope.launch {
            val ids = (0 until c.mediaItemCount).mapNotNull { i ->
                c.getMediaItemAt(i).mediaId.toLongOrNull()
            }
            val songsById = dao.getSongsByIds(ids).associateBy { it.id }
            val orderedSongs = ids.mapNotNull { songsById[it] }
            if (orderedSongs.isNotEmpty()) {
                val index = c.currentMediaItemIndex
                _uiState.value = _uiState.value.copy(
                    queue = orderedSongs,
                    currentIndex = index,
                    currentSong = orderedSongs.getOrNull(index),
                    durationMs = c.duration.coerceAtLeast(0)
                )
            }
        }
    }

    private fun attachListener() {
        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateCurrentSongFromIndex()
                persistQueueSnapshot()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _uiState.value = _uiState.value.copy(
                        durationMs = controller?.duration?.coerceAtLeast(0) ?: 0
                    )
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _uiState.value = _uiState.value.copy(shuffleEnabled = shuffleModeEnabled)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _uiState.value = _uiState.value.copy(repeatMode = repeatMode)
            }
        })
    }

    private fun updateCurrentSongFromIndex() {
        val index = controller?.currentMediaItemIndex ?: -1
        val song = _uiState.value.queue.getOrNull(index)
        _uiState.value = _uiState.value.copy(
            currentSong = song,
            currentIndex = index,
            durationMs = controller?.duration?.coerceAtLeast(0) ?: 0
        )
    }

    fun playQueue(songs: List<Song>, startIndex: Int) {
        val items = songs.map { it.toMediaItem() }
        _uiState.value = _uiState.value.copy(
            queue = songs,
            currentSong = songs.getOrNull(startIndex),
            currentIndex = startIndex
        )
        controller?.setMediaItems(items, startIndex, 0L)
        controller?.prepare()
        controller?.play()
        persistQueueSnapshot()
    }

    fun playNext(song: Song) {
        val insertIndex = (controller?.currentMediaItemIndex ?: 0) + 1
        controller?.addMediaItem(insertIndex, song.toMediaItem())
        val newQueue = _uiState.value.queue.toMutableList().apply { add(insertIndex, song) }
        _uiState.value = _uiState.value.copy(queue = newQueue)
        persistQueueSnapshot()
    }

    fun addToQueueEnd(song: Song) {
        controller?.addMediaItem(song.toMediaItem())
        _uiState.value = _uiState.value.copy(queue = _uiState.value.queue + song)
        persistQueueSnapshot()
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun skipNext() = controller?.seekToNextMediaItem()
    fun skipPrevious() = controller?.seekToPreviousMediaItem()
    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs)

    fun setShuffle(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
        _uiState.value = _uiState.value.copy(shuffleEnabled = enabled)
    }

    fun cycleRepeatMode() {
        val next = when (controller?.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller?.repeatMode = next
        _uiState.value = _uiState.value.copy(repeatMode = next)
    }

    fun currentPositionMs(): Long = controller?.currentPosition ?: 0

    // ---------- Fila persistente ----------

    private fun startPeriodicPositionSave() {
        scope.launch {
            while (true) {
                delay(5000)
                if (_uiState.value.isPlaying) persistQueueSnapshot()
            }
        }
    }

    /** Força salvar o estado atual da fila imediatamente (chamado em onStop da Activity). */
    fun persistNow() = persistQueueSnapshot()

    private fun persistQueueSnapshot() {
        val state = _uiState.value
        if (state.queue.isEmpty()) return
        scope.launch {
            settings.saveQueueState(
                songIds = state.queue.map { it.id },
                currentIndex = state.currentIndex.coerceAtLeast(0),
                positionMs = currentPositionMs()
            )
        }
    }

    // ---------- Sleep timer ----------

    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        val endAt = System.currentTimeMillis() + minutes * 60_000L
        _uiState.value = _uiState.value.copy(sleepTimerEndAt = endAt)
        sleepTimerJob = scope.launch {
            while (true) {
                val remaining = endAt - System.currentTimeMillis()
                if (remaining <= 0) {
                    controller?.pause()
                    _uiState.value = _uiState.value.copy(sleepTimerEndAt = null, sleepTimerRemainingMs = 0)
                    break
                }
                _uiState.value = _uiState.value.copy(sleepTimerRemainingMs = remaining)
                delay(1000)
            }
        }
    }

    fun stopAtEndOfSong() {
        cancelSleepTimer()
        val targetIndex = controller?.currentMediaItemIndex
        if (targetIndex == null) return
        sleepTimerJob = scope.launch {
            while (controller?.currentMediaItemIndex == targetIndex) {
                delay(500)
            }
            controller?.pause()
            _uiState.value = _uiState.value.copy(sleepTimerEndAt = null)
        }
        _uiState.value = _uiState.value.copy(sleepTimerEndAt = -1L)
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _uiState.value = _uiState.value.copy(sleepTimerEndAt = null, sleepTimerRemainingMs = 0)
    }

    fun release() {
        cancelSleepTimer()
        persistQueueSnapshot()
        controller?.release()
        controller = null
    }

    private fun Song.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setUri(path)
            .setMediaId(id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .build()
            )
            .build()
}
