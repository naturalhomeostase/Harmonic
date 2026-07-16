package com.harmonic.player.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "harmonic_settings")

data class SavedQueueState(val songIds: List<Long>, val currentIndex: Int, val positionMs: Long)

/** Fundos de tela padrão já embutidos no app (assets/default_wallpapers). */
enum class DefaultWallpaper(val assetPath: String, val label: String) {
    LION_FIRE("default_wallpapers/wallpaper_lion_fire.jpg", "Leão em chamas"),
    GUITAR_STORM("default_wallpapers/wallpaper_guitar_storm.jpg", "Guitarra elétrica"),
    VINYL_RAIN("default_wallpapers/wallpaper_vinyl_rain.jpg", "Toca-discos"),
    FOREST_MELODY("default_wallpapers/wallpaper_forest_melody.jpg", "Floresta encantada"),
    LOFI_CITY("default_wallpapers/wallpaper_lofi_city.jpg", "Cidade lo-fi")
}

/**
 * Temas em gradiente — não dependem de nenhuma imagem, então são muito mais
 * leves (sem decodificar JPEG nenhum) e servem como fundo padrão do app.
 * As cores são definidas como Long (ARGB) em vez de Color pra esse arquivo
 * não precisar depender do Compose.
 */
enum class GradientTheme(val label: String, val colorsArgb: List<Long>) {
    // Cores mais explícitas/vivas que a v1 (3 paradas em vez de 2 na
    // maioria), pra ficar bonito tanto como fundo quanto como gradiente de
    // texto — ainda escuro o bastante pra manter o texto branco legível.
    MIDNIGHT("Meia-noite", listOf(0xFF020024, 0xFF090979, 0xFF00B4DB)),
    SUNSET("Pôr do sol", listOf(0xFFFFD200, 0xFFFF512F, 0xFFDD2476)),
    OCEAN("Oceano", listOf(0xFF000428, 0xFF004E92, 0xFF00D4FF)),
    FOREST("Floresta", listOf(0xFF0F2027, 0xFF134E5E, 0xFF00C853)),
    ROSE("Rosé", listOf(0xFF2C0E37, 0xFF6B2D5C, 0xFFFF6FB5)),
    NEON("Neon", listOf(0xFF7303C0, 0xFFEC38BC, 0xFFFDEFF9)),
    MONO("Mono (mais leve)", listOf(0xFF161616, 0xFF0A0A0A))
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ACCENT_COLOR = intPreferencesKey("accent_color")
        val USE_ALBUM_ART_COLOR = booleanPreferencesKey("use_album_art_color")
        val BACKGROUND_URI = stringPreferencesKey("background_uri") // imagem custom do usuário
        val DEFAULT_WALLPAPER = stringPreferencesKey("default_wallpaper") // nome do enum acima
        val GRADIENT_THEME = stringPreferencesKey("gradient_theme") // nome do enum GradientTheme
        val BACKGROUND_BLUR_ENABLED = booleanPreferencesKey("background_blur_enabled")
        val BACKGROUND_BLUR_RADIUS = intPreferencesKey("background_blur_radius") // em dp, 0-40
        val BACKGROUND_SCRIM_ALPHA = intPreferencesKey("background_scrim_alpha") // 0-100 (%)
        val TITLE_GRADIENT_ENABLED = booleanPreferencesKey("title_gradient_enabled")
        val TITLE_GRADIENT_MODE = stringPreferencesKey("title_gradient_mode") // "theme" | "monochrome"
        val THEME_MODE = stringPreferencesKey("theme_mode") // "light" | "dark" | "amoled" | "system"
        val IGNORED_FOLDERS = stringSetPreferencesKey("ignored_folders")
        val CROSSFADE_MS = intPreferencesKey("crossfade_ms")
        val REPLAY_GAIN_ENABLED = booleanPreferencesKey("replay_gain_enabled")

        // Equalizador
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_BAND_LEVELS = stringPreferencesKey("eq_band_levels") // CSV, um valor por banda (milibels)
        val EQ_PRESET_NAME = stringPreferencesKey("eq_preset_name")
        val BASS_BOOST_STRENGTH = intPreferencesKey("bass_boost_strength") // 0-1000
        val VIRTUALIZER_STRENGTH = intPreferencesKey("virtualizer_strength") // 0-1000
        val REVERB_PRESET = intPreferencesKey("reverb_preset") // índice do PresetReverb

        // Fila persistente (restaurada quando o serviço reinicia)
        val SAVED_QUEUE_IDS = stringPreferencesKey("saved_queue_ids") // CSV de Song.id
        val SAVED_QUEUE_INDEX = intPreferencesKey("saved_queue_index")
        val SAVED_QUEUE_POSITION_MS = longPreferencesKey("saved_queue_position_ms")

        // Sleep timer
        val SLEEP_TIMER_END_AT = longPreferencesKey("sleep_timer_end_at") // epoch ms, 0 = desativado
    }

    val accentColor: Flow<Int?> = context.dataStore.data.map { it[Keys.ACCENT_COLOR] }
    val useAlbumArtColor: Flow<Boolean> = context.dataStore.data.map { it[Keys.USE_ALBUM_ART_COLOR] ?: true }
    val backgroundUri: Flow<String?> = context.dataStore.data.map { it[Keys.BACKGROUND_URI] }
    val defaultWallpaper: Flow<String?> = context.dataStore.data.map { it[Keys.DEFAULT_WALLPAPER] }
    val gradientTheme: Flow<String?> = context.dataStore.data.map { it[Keys.GRADIENT_THEME] }
    val backgroundBlurEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.BACKGROUND_BLUR_ENABLED] ?: false }
    val backgroundBlurRadius: Flow<Int> = context.dataStore.data.map { it[Keys.BACKGROUND_BLUR_RADIUS] ?: 10 }
    val backgroundScrimAlpha: Flow<Int> = context.dataStore.data.map { it[Keys.BACKGROUND_SCRIM_ALPHA] ?: 45 }
    // Gradiente também nos títulos das listas (opcional) — usa as mesmas
    // cores do tema de gradiente ativo (ou "Meia-noite" se o fundo for uma
    // imagem/foto, já que nesse caso não há uma paleta de gradiente ativa).
    val titleGradientEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.TITLE_GRADIENT_ENABLED] ?: false }
    // "theme" = usa as cores do tema de gradiente ativo (várias cores).
    // "monochrome" = usa só a cor de destaque, indo de mais clara pra mais
    // escura — mais discreto, e funciona bem mesmo com fundo de foto/imagem.
    val titleGradientMode: Flow<String> = context.dataStore.data.map { it[Keys.TITLE_GRADIENT_MODE] ?: "theme" }
    // Padrão "dark", não "system": o app sempre mostra uma imagem de fundo
    // com véu escuro por cima, então texto escuro (o que aconteceria no
    // tema claro do sistema) fica ilegível. Continua possível escolher
    // "light" manualmente se a pessoa realmente quiser.
    val themeMode: Flow<String> = context.dataStore.data.map { it[Keys.THEME_MODE] ?: "dark" }
    val ignoredFolders: Flow<Set<String>> = context.dataStore.data.map { it[Keys.IGNORED_FOLDERS] ?: emptySet() }
    val crossfadeMs: Flow<Int> = context.dataStore.data.map { it[Keys.CROSSFADE_MS] ?: 0 }
    val replayGainEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.REPLAY_GAIN_ENABLED] ?: false }

    suspend fun setAccentColor(colorArgb: Int) {
        context.dataStore.edit { it[Keys.ACCENT_COLOR] = colorArgb; it[Keys.USE_ALBUM_ART_COLOR] = false }
    }

    suspend fun setUseAlbumArtColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USE_ALBUM_ART_COLOR] = enabled }
    }

    suspend fun setCustomBackground(uri: String) {
        context.dataStore.edit {
            it[Keys.BACKGROUND_URI] = uri
            it.remove(Keys.DEFAULT_WALLPAPER)
            it.remove(Keys.GRADIENT_THEME)
        }
    }

    suspend fun setDefaultWallpaper(wallpaper: DefaultWallpaper) {
        context.dataStore.edit {
            it[Keys.DEFAULT_WALLPAPER] = wallpaper.name
            it.remove(Keys.BACKGROUND_URI)
            it.remove(Keys.GRADIENT_THEME)
        }
    }

    suspend fun setGradientTheme(theme: GradientTheme) {
        context.dataStore.edit {
            it[Keys.GRADIENT_THEME] = theme.name
            it.remove(Keys.BACKGROUND_URI)
            it.remove(Keys.DEFAULT_WALLPAPER)
        }
    }

    suspend fun setBackgroundBlurEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BACKGROUND_BLUR_ENABLED] = enabled }
    }

    suspend fun setBackgroundBlurRadius(radiusDp: Int) {
        context.dataStore.edit { it[Keys.BACKGROUND_BLUR_RADIUS] = radiusDp }
    }

    suspend fun setBackgroundScrimAlpha(alphaPercent: Int) {
        context.dataStore.edit { it[Keys.BACKGROUND_SCRIM_ALPHA] = alphaPercent }
    }

    suspend fun setTitleGradientEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TITLE_GRADIENT_ENABLED] = enabled }
    }

    suspend fun setTitleGradientMode(mode: String) {
        context.dataStore.edit { it[Keys.TITLE_GRADIENT_MODE] = mode }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun addIgnoredFolder(path: String) {
        context.dataStore.edit { it[Keys.IGNORED_FOLDERS] = (it[Keys.IGNORED_FOLDERS] ?: emptySet()) + path }
    }

    suspend fun removeIgnoredFolder(path: String) {
        context.dataStore.edit { it[Keys.IGNORED_FOLDERS] = (it[Keys.IGNORED_FOLDERS] ?: emptySet()) - path }
    }

    suspend fun setCrossfadeMs(ms: Int) {
        context.dataStore.edit { it[Keys.CROSSFADE_MS] = ms }
    }

    suspend fun setReplayGainEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REPLAY_GAIN_ENABLED] = enabled }
    }

    // ---------- Equalizador ----------

    val eqEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.EQ_ENABLED] ?: false }
    val eqBandLevels: Flow<List<Int>> = context.dataStore.data.map { prefs ->
        prefs[Keys.EQ_BAND_LEVELS]?.split(",")?.mapNotNull { it.toIntOrNull() } ?: List(10) { 0 }
    }
    val eqPresetName: Flow<String> = context.dataStore.data.map { it[Keys.EQ_PRESET_NAME] ?: "Personalizado" }
    val bassBoostStrength: Flow<Int> = context.dataStore.data.map { it[Keys.BASS_BOOST_STRENGTH] ?: 0 }
    val virtualizerStrength: Flow<Int> = context.dataStore.data.map { it[Keys.VIRTUALIZER_STRENGTH] ?: 0 }
    val reverbPreset: Flow<Int> = context.dataStore.data.map { it[Keys.REVERB_PRESET] ?: 0 }

    suspend fun setEqEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EQ_ENABLED] = enabled }
    }

    suspend fun setEqBandLevels(levels: List<Int>, presetName: String = "Personalizado") {
        context.dataStore.edit {
            it[Keys.EQ_BAND_LEVELS] = levels.joinToString(",")
            it[Keys.EQ_PRESET_NAME] = presetName
        }
    }

    suspend fun setBassBoostStrength(strength: Int) {
        context.dataStore.edit { it[Keys.BASS_BOOST_STRENGTH] = strength }
    }

    suspend fun setVirtualizerStrength(strength: Int) {
        context.dataStore.edit { it[Keys.VIRTUALIZER_STRENGTH] = strength }
    }

    suspend fun setReverbPreset(preset: Int) {
        context.dataStore.edit { it[Keys.REVERB_PRESET] = preset }
    }

    // ---------- Fila persistente ----------

    suspend fun saveQueueState(songIds: List<Long>, currentIndex: Int, positionMs: Long) {
        context.dataStore.edit {
            it[Keys.SAVED_QUEUE_IDS] = songIds.joinToString(",")
            it[Keys.SAVED_QUEUE_INDEX] = currentIndex
            it[Keys.SAVED_QUEUE_POSITION_MS] = positionMs
        }
    }

    suspend fun readSavedQueueState(): SavedQueueState? {
        val prefs = context.dataStore.data.first()
        val idsCsv = prefs[Keys.SAVED_QUEUE_IDS] ?: return null
        val ids = idsCsv.split(",").mapNotNull { it.toLongOrNull() }
        if (ids.isEmpty()) return null
        return SavedQueueState(
            songIds = ids,
            currentIndex = prefs[Keys.SAVED_QUEUE_INDEX] ?: 0,
            positionMs = prefs[Keys.SAVED_QUEUE_POSITION_MS] ?: 0L
        )
    }

    // ---------- Sleep timer ----------

    val sleepTimerEndAt: Flow<Long> = context.dataStore.data.map { it[Keys.SLEEP_TIMER_END_AT] ?: 0L }

    suspend fun setSleepTimerEndAt(epochMs: Long) {
        context.dataStore.edit { it[Keys.SLEEP_TIMER_END_AT] = epochMs }
    }
}
