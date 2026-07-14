package com.harmonic.player.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.harmonic.player.HarmonicApp
import com.harmonic.player.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Serviço de reprodução em segundo plano.
 *
 * Usar MediaSessionService (Media3) nos dá de graça, sem código extra:
 *  - Notificação com controles (play/pause/próxima/anterior/capa)
 *  - Controles na tela bloqueada
 *  - Resposta automática a botões do fone Bluetooth / fone com fio
 *  - Pausa automática ao receber ligação ou desconectar o áudio
 *  - Compatibilidade com Android Auto
 *
 * O ExoPlayer já lida nativamente com MP3, FLAC, WAV, AAC, OGG, OPUS, M4A.
 *
 * Também restaura a última fila de reprodução salva (ver
 * [com.harmonic.player.data.SettingsRepository.saveQueueState]) assim que o
 * serviço é criado, deixando o player pronto (mas pausado) — assim, se o
 * usuário reabrir o app depois de o sistema matar o processo, a música que
 * estava tocando continua exatamente de onde parou.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var restoreJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.repeatMode = Player.REPEAT_MODE_OFF

        // Registra o player no holder — é o que permite ao widget de tela
        // inicial controlar a reprodução e mostrar o que está tocando.
        PlaybackServiceHolder.attach(player)

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = updateWidget()
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = updateWidget()
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updateWidget()
        })

        // O audioSessionId só é acessível aqui, na instância real do
        // ExoPlayer — expomos ele pro resto do app (equalizador) via
        // PlaybackAudioSession, já que MediaController não tem esse dado.
        player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                PlaybackAudioSession.update(audioSessionId)
            }
        })

        val sessionActivityIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .setCallback(PlaybackSessionCallback())
            .build()

        restoreSavedQueueIfAny(player)
    }

    private fun restoreSavedQueueIfAny(player: ExoPlayer) {
        restoreJob = serviceScope.launch {
            val app = applicationContext as HarmonicApp
            val saved = app.settings.readSavedQueueState() ?: return@launch
            val dao = app.database.songDao()
            val songsById = dao.getSongsByIds(saved.songIds).associateBy { it.id }
            val orderedSongs = saved.songIds.mapNotNull { songsById[it] }
            if (orderedSongs.isEmpty()) return@launch

            val items = orderedSongs.map { song ->
                MediaItem.Builder()
                    .setUri(song.path)
                    .setMediaId(song.id.toString())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .build()
                    )
                    .build()
            }
            val safeIndex = saved.currentIndex.coerceIn(0, items.size - 1)
            player.setMediaItems(items, safeIndex, saved.positionMs)
            player.prepare()
            // Sem player.play() — fica pronto, pausado, esperando o usuário.
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        // Se não estiver tocando, encerra o serviço junto com o app (economiza bateria);
        // se estiver tocando, mantém rodando em segundo plano.
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        restoreJob?.cancel()
        PlaybackServiceHolder.detach()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    /**
     * Atualiza o estado que o widget lê e dispara o redesenho dele. Rodar
     * isso a cada mudança de faixa/play-pause é barato — o Glance só
     * recompõe de fato quando algo no estado realmente muda.
     */
    private fun updateWidget() {
        PlaybackServiceHolder.refreshState()
        serviceScope.launch {
            com.harmonic.player.widget.HarmonicWidget().updateAll(applicationContext)
        }
    }
}

/**
 * Callback da sessão — ponto de extensão para fila persistente customizada
 * no futuro (ex: onPlaybackResumption). Por enquanto usa o comportamento
 * padrão do Media3, que já cobre play/pause/próxima/anterior/seek/shuffle/repeat.
 */
private class PlaybackSessionCallback : MediaSession.Callback {
    // Sobrescreveremos onAddMediaItems/onPlaybackResumption aqui numa
    // próxima fase, se precisarmos customizar o comportamento padrão.
}
