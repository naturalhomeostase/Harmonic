package com.harmonic.player.playback

import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WidgetPlaybackState(
    val title: String? = null,
    val artist: String? = null,
    val isPlaying: Boolean = false,
    val hasQueue: Boolean = false
)

/**
 * Ponte entre o widget de tela inicial (Glance) e o player real, que só
 * existe dentro do `PlaybackService`. Como o serviço roda no mesmo processo
 * do app, guardar a referência do `Player` aqui (populada pelo próprio
 * serviço) é o caminho mais direto pro widget ler/controlar a reprodução,
 * sem precisar montar um MediaController próprio só pra isso.
 */
object PlaybackServiceHolder {
    private var player: Player? = null

    private val _state = MutableStateFlow(WidgetPlaybackState())
    val state: StateFlow<WidgetPlaybackState> = _state.asStateFlow()

    fun attach(player: Player) {
        this.player = player
    }

    fun detach() {
        player = null
        _state.value = WidgetPlaybackState()
    }

    fun refreshState() {
        val p = player ?: run { _state.value = WidgetPlaybackState(); return }
        val metadata = p.mediaMetadata
        _state.value = WidgetPlaybackState(
            title = metadata.title?.toString(),
            artist = metadata.artist?.toString(),
            isPlaying = p.isPlaying,
            hasQueue = p.mediaItemCount > 0
        )
    }

    fun togglePlayPause() {
        player?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun skipNext() {
        player?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        player?.seekToPreviousMediaItem()
    }
}
