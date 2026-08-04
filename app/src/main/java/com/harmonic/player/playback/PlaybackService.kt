package com.harmonic.player.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.glance.appwidget.updateAll
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.harmonic.player.HarmonicApp
import com.harmonic.player.MainActivity
import com.harmonic.player.R
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
    val mediaSessionPublic: MediaSession? get() = mediaSession
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

        val sessionCallback = PlaybackSessionCallback(this, serviceScope)
        // Quando o coração é tocado dentro do app (não na notificação), o
        // ícone da notificação precisa saber disso também — sem essa ponte,
        // ele só atualizava ao trocar de música.
        PlaybackServiceHolder.setFavoriteChangeListener { songId, isFavorite ->
            sessionCallback.onExternalFavoriteChange(songId, isFavorite)
        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateWidget()
                // Reforça o valor atual da sessão de áudio a cada troca de
                // play/pause — o listener de análise abaixo (que só reage
                // quando o ID realmente MUDA) sozinho não estava sendo o
                // bastante pra manter o equalizador conectado em todos os
                // casos reais de uso; isso é uma rede de segurança extra,
                // barata de chamar.
                PlaybackAudioSession.update(player.audioSessionId)
            }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = updateWidget()
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateWidget()
                sessionCallback.onSongChanged(mediaItem?.mediaId?.toLongOrNull())
                PlaybackAudioSession.update(player.audioSessionId)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                PlaybackAudioSession.update(player.audioSessionId)
            }
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
            .setCallback(sessionCallback)
            .build()

        // Sem isso, a notificação de reprodução usa um canal e um nome
        // genéricos escolhidos pela própria biblioteca — dando um nome
        // claro aqui, fica mais fácil pro usuário achar e conferir se as
        // notificações do player estão ativadas em Ajustes > Apps > Music
        // Box > Notificações, caso não estejam aparecendo.
        val notificationProvider = androidx.media3.session.DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setChannelName(R.string.notification_channel_playback)
            .build()
        setMediaNotificationProvider(notificationProvider)

        restoreSavedQueueIfAny(player)
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "harmonic_playback"
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
        // Mantém o serviço (e a notificação com os controles) vivo sempre
        // que existir uma fila carregada — mesmo pausado. Antes, fechar o
        // app com a música pausada matava a notificação junto, obrigando a
        // reabrir o app só pra retomar. Só encerra de vez se não há
        // absolutamente nada carregado pra tocar.
        if (player == null || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        restoreJob?.cancel()
        PlaybackServiceHolder.setFavoriteChangeListener(null)
        PlaybackServiceHolder.detach()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    /**
     * Atualiza o estado que os widgets leem e dispara o redesenho deles.
     * Rodar isso a cada mudança de faixa/play-pause é barato — o Glance só
     * recompõe de fato quando algo no estado realmente muda.
     *
     * A capa é buscada à parte, em segundo plano: é a parte "cara" (pode
     * envolver ler o arquivo de áudio ou até baixar da internet), então os
     * widgets já atualizam texto/ícone de play na hora, e a capa aparece
     * assim que estiver pronta — sem travar a resposta do botão.
     */
    private fun updateWidget() {
        PlaybackServiceHolder.refreshState()
        val mediaId = PlaybackServiceHolder.state.value.currentMediaId
        serviceScope.launch { updateAllWidgets() }

        if (mediaId != null && PlaybackServiceHolder.state.value.coverBitmap == null) {
            serviceScope.launch(Dispatchers.IO) {
                val dao = (applicationContext as HarmonicApp).database.songDao()
                val song = dao.getSongsByIds(listOf(mediaId)).firstOrNull()
                // Tamanho pequeno (300px) — a capa no widget nunca aparece
                // maior que uma tela cheia pequena, e um bitmap menor custa
                // bem menos memória/bateria pra decodificar e desenhar.
                val bitmap = song?.let { com.harmonic.player.data.AlbumArtLoader.load(applicationContext, it, sizePx = 300) }
                PlaybackServiceHolder.updateCover(mediaId, bitmap)
                updateAllWidgets()
            }
        }
    }

    private suspend fun updateAllWidgets() {
        com.harmonic.player.widget.HarmonicWidgetSmall().updateAll(applicationContext)
        com.harmonic.player.widget.HarmonicWidgetMedium().updateAll(applicationContext)
        com.harmonic.player.widget.HarmonicWidgetLarge().updateAll(applicationContext)
    }
}

/**
 * Callback da sessão — ponto de extensão para fila persistente customizada
 * no futuro (ex: onPlaybackResumption). Por enquanto usa o comportamento
 * padrão do Media3, que já cobre play/pause/próxima/anterior/seek/shuffle/repeat.
 *
 * Também adiciona dois botões customizados na notificação:
 *  - "Favoritar" — o coração reflete o estado real da música atual (e se
 *    atualiza sozinho tanto ao trocar de música quanto quando o usuário
 *    favorita pelo app com a notificação já aberta).
 *  - "Parar" (STOP) — diferente do pause, encerra a reprodução de vez
 *    (limpa a fila), pra quem quer fechar a música rapidinho sem precisar
 *    abrir o app.
 */
private const val ACTION_STOP = "com.harmonic.player.STOP"
private const val ACTION_FAVORITE_TOGGLE = "com.harmonic.player.FAVORITE_TOGGLE"

private class PlaybackSessionCallback(
    private val context: Context,
    private val scope: CoroutineScope
) : MediaSession.Callback {

    private val stopSessionCommand = SessionCommand(ACTION_STOP, Bundle.EMPTY)
    private val favoriteSessionCommand = SessionCommand(ACTION_FAVORITE_TOGGLE, Bundle.EMPTY)

    private val stopButton = CommandButton.Builder()
        .setDisplayName("Parar")
        .setSessionCommand(stopSessionCommand)
        .setIconResId(R.drawable.ic_stop)
        .build()

    private var currentSongId: Long? = null
    private var currentIsFavorite: Boolean = false

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        val availableCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
            .buildUpon()
            .add(stopSessionCommand)
            .add(favoriteSessionCommand)
            .build()
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(availableCommands)
            .build()
    }

    override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
        refreshCustomLayout(session)
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        when (customCommand.customAction) {
            ACTION_STOP -> {
                session.player.stop()
                session.player.clearMediaItems()
            }
            ACTION_FAVORITE_TOGGLE -> {
                val songId = currentSongId
                if (songId != null) {
                    val newValue = !currentIsFavorite
                    // Otimista: já reflete o novo estado no ícone na hora,
                    // sem esperar a escrita no banco terminar — a notificação
                    // fica travada só o tempo de um toggle de boolean.
                    currentIsFavorite = newValue
                    refreshCustomLayout(session)
                    scope.launch(Dispatchers.IO) {
                        val dao = (context.applicationContext as HarmonicApp).database.songDao()
                        dao.setFavorite(songId, newValue)
                    }
                    PlaybackServiceHolder.notifyFavoriteChanged(songId, newValue)
                }
            }
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    /** Chamado pelo listener do player quando a faixa muda — busca o status de favorito da música nova. */
    fun onSongChanged(songId: Long?) {
        currentSongId = songId
        if (songId == null) {
            currentIsFavorite = false
            return
        }
        scope.launch(Dispatchers.IO) {
            val dao = (context.applicationContext as HarmonicApp).database.songDao()
            val isFavorite = dao.getSongsByIds(listOf(songId)).firstOrNull()?.isFavorite ?: false
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                // Confere se a música não mudou de novo enquanto a consulta
                // rodava (troca rápida de faixas) antes de aplicar.
                if (currentSongId == songId) {
                    currentIsFavorite = isFavorite
                    context.mediaSessionOrNull()?.let { refreshCustomLayout(it) }
                }
            }
        }
    }

    /** Chamado quando o favorito muda por FORA da notificação (ex: coração na tela do app) — mantém o ícone sincronizado. */
    fun onExternalFavoriteChange(songId: Long, isFavorite: Boolean) {
        if (songId == currentSongId && isFavorite != currentIsFavorite) {
            currentIsFavorite = isFavorite
            context.mediaSessionOrNull()?.let { refreshCustomLayout(it) }
        }
    }

    private fun refreshCustomLayout(session: MediaSession) {
        val favoriteButton = CommandButton.Builder()
            .setDisplayName(if (currentIsFavorite) "Remover dos favoritos" else "Favoritar")
            .setSessionCommand(favoriteSessionCommand)
            .setIconResId(if (currentIsFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border)
            .build()
        session.setCustomLayout(listOf(favoriteButton, stopButton))
    }
}

/** [Context] aqui é sempre o próprio [PlaybackService] — pega a sessão dele de volta pra atualizar o layout fora do fluxo normal de callbacks. */
private fun Context.mediaSessionOrNull(): MediaSession? = (this as? PlaybackService)?.mediaSessionPublic
