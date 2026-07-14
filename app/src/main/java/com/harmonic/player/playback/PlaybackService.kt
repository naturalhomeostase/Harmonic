package com.harmonic.player.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.harmonic.player.MainActivity

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
 * AIFF entra via extensão FFmpeg (fase 2, opcional).
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            // Pausa automaticamente ao desconectar fone/Bluetooth
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.repeatMode = Player.REPEAT_MODE_OFF

        val sessionActivityIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .setCallback(PlaybackSessionCallback())
            .build()
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
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

/**
 * Callback da sessão — ponto de extensão para "Tocar em seguida", fila
 * persistente, ReplayGain, etc. Por enquanto usa o comportamento padrão do
 * Media3, que já cobre play/pause/próxima/anterior/seek/shuffle/repeat.
 */
private class PlaybackSessionCallback : MediaSession.Callback {
    // Sobrescreveremos onAddMediaItems/onPlaybackResumption aqui nas
    // próximas fases (fila persistente, "tocar em seguida", etc.)
}
