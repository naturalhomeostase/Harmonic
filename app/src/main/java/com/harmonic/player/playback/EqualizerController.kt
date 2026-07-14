package com.harmonic.player.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqualizerBandInfo(val index: Int, val centerFreqHz: Int, val minLevel: Int, val maxLevel: Int)

data class EqualizerUiState(
    val ready: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<EqualizerBandInfo> = emptyList(),
    val bandLevels: List<Int> = emptyList(),
    val bassBoostStrength: Int = 0,
    val virtualizerStrength: Int = 0,
    val reverbPreset: Int = 0
)

/**
 * Os efeitos de áudio do Android (`android.media.audiofx.*`) se conectam a
 * uma "sessão de áudio" (audioSessionId) — um valor específico do ExoPlayer
 * (não da interface genérica `Player` usada pelo MediaController do lado da
 * UI), por isso ele chega até aqui via o singleton `PlaybackAudioSession`,
 * atualizado de dentro do `PlaybackService`.
 *
 * IMPORTANTE: os efeitos precisam ser recriados sempre que o audioSessionId
 * mudar, por isso expomos `attach(sessionId)` para ser chamado sempre que
 * `PlaybackAudioSession.sessionId` mudar.
 */
class EqualizerController {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var presetReverb: PresetReverb? = null
    private var currentSessionId: Int = 0

    private val _uiState = MutableStateFlow(EqualizerUiState())
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()

    fun attach(sessionId: Int) {
        if (sessionId == 0 || sessionId == currentSessionId) return
        release()
        currentSessionId = sessionId

        try {
            val eq = Equalizer(0, sessionId).apply { enabled = _uiState.value.enabled }
            val bands = (0 until eq.numberOfBands).map { i ->
                val idx = i.toShort()
                EqualizerBandInfo(
                    index = i,
                    centerFreqHz = eq.getCenterFreq(idx) / 1000,
                    minLevel = eq.bandLevelRange[0].toInt(),
                    maxLevel = eq.bandLevelRange[1].toInt()
                )
            }
            equalizer = eq

            val bb = BassBoost(0, sessionId).apply { enabled = _uiState.value.enabled }
            bassBoost = bb

            val vr = Virtualizer(0, sessionId).apply { enabled = _uiState.value.enabled }
            virtualizer = vr

            val reverb = PresetReverb(0, sessionId).apply { enabled = false }
            presetReverb = reverb

            _uiState.value = _uiState.value.copy(ready = true, bands = bands)

            // Reaplica os valores salvos assim que os efeitos são criados
            reapplyCurrentState()
        } catch (e: Exception) {
            // Alguns aparelhos (principalmente com ROMs customizadas) não
            // implementam todos os efeitos — o app continua funcionando
            // sem equalizador em vez de travar.
            _uiState.value = _uiState.value.copy(ready = false)
        }
    }

    private fun reapplyCurrentState() {
        setEnabled(_uiState.value.enabled)
        _uiState.value.bandLevels.forEachIndexed { index, level -> setBandLevel(index, level) }
        setBassBoostStrength(_uiState.value.bassBoostStrength)
        setVirtualizerStrength(_uiState.value.virtualizerStrength)
        setReverbPreset(_uiState.value.reverbPreset)
    }

    fun setEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
        bassBoost?.enabled = enabled
        virtualizer?.enabled = enabled
        presetReverb?.enabled = enabled && _uiState.value.reverbPreset != 0
        _uiState.value = _uiState.value.copy(enabled = enabled)
    }

    fun setBandLevel(bandIndex: Int, level: Int) {
        equalizer?.setBandLevel(bandIndex.toShort(), level.toShort())
        val updated = _uiState.value.bandLevels.toMutableList()
        while (updated.size <= bandIndex) updated.add(0)
        updated[bandIndex] = level
        _uiState.value = _uiState.value.copy(bandLevels = updated)
    }

    fun setBassBoostStrength(strength: Int) {
        bassBoost?.setStrength(strength.toShort())
        _uiState.value = _uiState.value.copy(bassBoostStrength = strength)
    }

    fun setVirtualizerStrength(strength: Int) {
        virtualizer?.setStrength(strength.toShort())
        _uiState.value = _uiState.value.copy(virtualizerStrength = strength)
    }

    fun setReverbPreset(preset: Int) {
        presetReverb?.let {
            it.preset = preset.toShort()
            it.enabled = _uiState.value.enabled && preset != 0
        }
        _uiState.value = _uiState.value.copy(reverbPreset = preset)
    }

    /** Chamado ao carregar os valores salvos no DataStore, antes mesmo dos efeitos existirem. */
    fun restoreState(
        enabled: Boolean,
        bandLevels: List<Int>,
        bassBoost: Int,
        virtualizer: Int,
        reverbPreset: Int
    ) {
        _uiState.value = _uiState.value.copy(
            enabled = enabled,
            bandLevels = bandLevels,
            bassBoostStrength = bassBoost,
            virtualizerStrength = virtualizer,
            reverbPreset = reverbPreset
        )
        if (_uiState.value.ready) reapplyCurrentState()
    }

    fun release() {
        equalizer?.release(); equalizer = null
        bassBoost?.release(); bassBoost = null
        virtualizer?.release(); virtualizer = null
        presetReverb?.release(); presetReverb = null
        currentSessionId = 0
    }
}

/** Nomes amigáveis para os presets do PresetReverb (fixos no Android, 0 = nenhum). */
val reverbPresetNames = listOf(
    "Nenhum", "Ambiente pequeno", "Sala média", "Sala grande",
    "Câmara média", "Câmara grande", "Plate"
)
