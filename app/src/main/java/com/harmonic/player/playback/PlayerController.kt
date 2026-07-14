package com.harmonic.player.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.harmonic.player.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1
)

/**
 * Envolve o MediaController do Media3 numa API simples de usar a partir do
 * Compose. Uma única instância é compartilhada pela Activity.
 *
 * IMPORTANTE: a música atual é resolvida a partir da lista `queue` guardada
 * aqui (que colocamos na fila com playQueue) usando o índice reportado pelo
 * próprio controller — e não tentando reconstruir a Song a partir dos
 * metadados do MediaItem, que perderiam informações como bitrate, favorito,
 * contagem de reproduções etc.
 */
class PlayerController(private val context: Context) {

    private var controller: MediaController? = null

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    fun connect(onConnected: () -> Unit = {}) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            controller = controllerFuture.get()
            attachListener()
            onConnected()
        }, MoreExecutors.directExecutor())
    }

    private fun attachListener() {
        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateCurrentSongFromIndex()
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
        // Guarda a fila e já define a música atual de forma otimista, antes
        // mesmo do callback do player chegar — assim a tela "Agora Tocando"
        // nunca fica em branco por um instante ao trocar de música.
        _uiState.value = _uiState.value.copy(
            queue = songs,
            currentSong = songs.getOrNull(startIndex),
            currentIndex = startIndex
        )
        controller?.setMediaItems(items, startIndex, 0L)
        controller?.prepare()
        controller?.play()
    }

    fun playNext(song: Song) {
        val insertIndex = (controller?.currentMediaItemIndex ?: 0) + 1
        controller?.addMediaItem(insertIndex, song.toMediaItem())
        val newQueue = _uiState.value.queue.toMutableList().apply { add(insertIndex, song) }
        _uiState.value = _uiState.value.copy(queue = newQueue)
    }

    fun addToQueueEnd(song: Song) {
        controller?.addMediaItem(song.toMediaItem())
        _uiState.value = _uiState.value.copy(queue = _uiState.value.queue + song)
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

    fun release() {
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
