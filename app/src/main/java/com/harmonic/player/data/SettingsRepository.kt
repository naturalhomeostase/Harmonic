package com.harmonic.player.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "harmonic_settings")

/** Fundos de tela padrão já embutidos no app (assets/default_wallpapers). */
enum class DefaultWallpaper(val assetPath: String, val label: String) {
    LION_FIRE("default_wallpapers/wallpaper_lion_fire.jpg", "Leão em chamas"),
    GUITAR_STORM("default_wallpapers/wallpaper_guitar_storm.jpg", "Guitarra elétrica"),
    VINYL_RAIN("default_wallpapers/wallpaper_vinyl_rain.jpg", "Toca-discos"),
    FOREST_MELODY("default_wallpapers/wallpaper_forest_melody.jpg", "Floresta encantada"),
    LOFI_CITY("default_wallpapers/wallpaper_lofi_city.jpg", "Cidade lo-fi")
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ACCENT_COLOR = intPreferencesKey("accent_color")
        val USE_ALBUM_ART_COLOR = booleanPreferencesKey("use_album_art_color")
        val BACKGROUND_URI = stringPreferencesKey("background_uri") // imagem custom do usuário
        val DEFAULT_WALLPAPER = stringPreferencesKey("default_wallpaper") // nome do enum acima
        val THEME_MODE = stringPreferencesKey("theme_mode") // "light" | "dark" | "amoled" | "system"
        val IGNORED_FOLDERS = stringSetPreferencesKey("ignored_folders")
        val CROSSFADE_MS = intPreferencesKey("crossfade_ms")
        val REPLAY_GAIN_ENABLED = booleanPreferencesKey("replay_gain_enabled")
    }

    val accentColor: Flow<Int?> = context.dataStore.data.map { it[Keys.ACCENT_COLOR] }
    val useAlbumArtColor: Flow<Boolean> = context.dataStore.data.map { it[Keys.USE_ALBUM_ART_COLOR] ?: true }
    val backgroundUri: Flow<String?> = context.dataStore.data.map { it[Keys.BACKGROUND_URI] }
    val defaultWallpaper: Flow<String?> = context.dataStore.data.map { it[Keys.DEFAULT_WALLPAPER] }
    val themeMode: Flow<String> = context.dataStore.data.map { it[Keys.THEME_MODE] ?: "system" }
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
        }
    }

    suspend fun setDefaultWallpaper(wallpaper: DefaultWallpaper) {
        context.dataStore.edit {
            it[Keys.DEFAULT_WALLPAPER] = wallpaper.name
            it.remove(Keys.BACKGROUND_URI)
        }
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
}
