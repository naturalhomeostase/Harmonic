package com.harmonic.player.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "harmonic_settings")

data class SavedQueueState(val songIds: List<Long>, val currentIndex: Int, val positionMs: Long)

/** Fundos de tela padrão já embutidos no app (assets/default_wallpapers). */
enum class DefaultWallpaper(val assetPath: String, val label: String) {
    COZY_RECORD("default_wallpapers/wallpaper_cozy_record.jpg", "Tarde de chuva"),
    LOFI_TOKYO("default_wallpapers/wallpaper_lofi_tokyo.jpg", "Lo-fi Tóquio"),
    ENCHANTED_FOREST("default_wallpapers/wallpaper_enchanted_forest.jpg", "Floresta encantada"),
    VINYL_GALAXY("default_wallpapers/wallpaper_vinyl_galaxy.jpg", "Universo musical")
}

/**
 * Temas em gradiente — não dependem de nenhuma imagem, então são muito mais
 * leves (sem decodificar JPEG nenhum) e servem como fundo padrão do app.
 * As cores são definidas como Long (ARGB) em vez de Color pra esse arquivo
 * não precisar depender do Compose.
 */
enum class GradientTheme(val label: String, val colorsArgb: List<Long>) {
    // Tema padrão — cores tiradas do ícone atual do app (o gramofone
    // dourado): um âmbar/bronze mais rico numa ponta e um creme dourado
    // quente na outra, ecoando o metal e a caixinha de música do ícone,
    // só um pouco mais vivos/saturados do que uma amostra literal da
    // imagem ficaria (uma cor "crua" tirada do ícone tende a sair meio
    // sem graça/acinzentada como fundo de tela inteira).
    APP_ICON("Music Box", listOf(0xFFB6812B, 0xFFFFE9C4)),
    // Tirei os temas azuis, o rosa (Rosé) e o roxo (Neon) — eram
    // exatamente os que ficavam com a cor de destaque opaca/estranha.
    SUNSET("Pôr do sol", listOf(0xFFFFD200, 0xFFDD2476)),
    FOREST("Floresta", listOf(0xFF0F2027, 0xFF00C853)),
    MONO("Mono (mais leve)", listOf(0xFF161616, 0xFF0A0A0A)),
    // Os 3 abaixo voltaram a ter cores mais vivas/variadas (incluindo azul
    // e roxo de novo) porque o problema de opacidade era no GRADIENTE DO
    // TEXTO reaproveitando a cor crua do tema, e isso já foi corrigido na
    // origem (LibraryScreen.readableGradientTextColor) — não precisa mais
    // evitar certas cores de tema pra fugir do sintoma.
    AURORA("Aurora", listOf(0xFF00C9A7, 0xFF845EC2)),
    OCEAN("Oceano", listOf(0xFF002B5B, 0xFF00B4D8)),
    BERRY("Vinho", listOf(0xFF3A0CA3, 0xFFF72585))
}

class SettingsRepository(private val context: Context) {
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Lê o DataStore uma vez, na hora (bloqueando só a criação do
    // repositório, que já acontece bem cedo — antes do primeiro frame da
    // Activity) — e a partir daí mantém sempre o valor mais recente em
    // memória. Sem isso, TODA tela que faz `collectAsState(initial = ...)`
    // começa mostrando esse valor padrão "chutado" (tema/papel de parede
    // padrão) até a primeira leitura assíncrona do disco terminar — daí o
    // "glimpse" do tema padrão antes do tema escolhido aparecer, no
    // primeiro frame do app. Como esse StateFlow já nasce com o valor REAL
    // (lido de forma síncrona aqui), a primeira composição já usa o tema
    // certo direto, sem flash nenhum.
    private val data: StateFlow<Preferences> = context.dataStore.data.stateIn(
        scope = repoScope,
        started = SharingStarted.Eagerly,
        initialValue = runBlocking { context.dataStore.data.first() }
    )

    init {
        // Migração única: quem já tinha o app instalado (com o ícone/tema
        // antigo) e nunca escolheu uma cor de destaque própria acabava com
        // essa cor salva no disco mesmo assim — ela era o FALLBACK antigo
        // (rosa/salmão do ícone anterior), aplicado sempre que o tema
        // "Music Box" era selecionado. Trocar só o valor padrão no código
        // não muda o que já está gravado no aparelho de quem atualiza por
        // cima; sem isso, o app continuava salmão mesmo com ícone e tema já
        // dourados. Só limpa se o valor salvo bater EXATAMENTE com o tom
        // antigo (não mexe em nenhuma cor escolhida deliberadamente pelo
        // usuário na roda de cores).
        val oldDefaultPinks = setOf(0xFFE76895L.toInt(), 0xFFC23E74L.toInt())
        val savedAccent = data.value[Keys.ACCENT_COLOR]
        if (savedAccent != null && oldDefaultPinks.contains(savedAccent)) {
            repoScope.launch { context.dataStore.edit { it.remove(Keys.ACCENT_COLOR) } }
        }

        // Segunda migração: quem tinha escolhido um dos gradientes removidos
        // (o rosa "Rosé", o roxo "Neon" ou um dos azuis) ficou com o NOME
        // desse tema salvo no disco (ex: "ROSE"), que não bate com nenhum
        // valor do enum GradientTheme atual — todo lugar que resolve esse
        // nome já cai de volta no gradiente padrão (APP_ICON) com
        // `.find { ... } ?: GradientTheme.APP_ICON`, então o FUNDO parece
        // certo. O problema é que a cor de destaque (accent_color) e as
        // cores do gradiente de título, que tinham sido salvas junto quando
        // aquele tema antigo foi escolhido (a partir do rosa/roxo/azul dele),
        // continuavam presas no disco pra sempre — nada as limpava nesse
        // caminho de fallback silencioso. Resultado: o app mostrava o
        // gradiente novo (dourado) mas "Music Box", sublinhados, ícones etc
        // continuavam com a cor do tema antigo removido. Detectando esse
        // nome órfão aqui, aplicamos a MESMA limpeza que a tela de aparência
        // já faz ao selecionar "Music Box" manualmente: cor de destaque e
        // gradiente de título voltam a acompanhar o tema padrão.
        val savedGradientName = data.value[Keys.GRADIENT_THEME]
        val gradientIsOrphaned = savedGradientName != null &&
            GradientTheme.values().none { it.name == savedGradientName }
        if (gradientIsOrphaned) {
            repoScope.launch {
                context.dataStore.edit {
                    it.remove(Keys.GRADIENT_THEME)
                    it.remove(Keys.ACCENT_COLOR)
                    it[Keys.USE_ALBUM_ART_COLOR] = false
                    it.remove(Keys.TITLE_GRADIENT_COLOR_START)
                    it.remove(Keys.TITLE_GRADIENT_COLOR_END)
                }
            }
        }
    }

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
        val TITLE_GRADIENT_COLOR_START = intPreferencesKey("title_gradient_color_start")
        val TITLE_GRADIENT_COLOR_END = intPreferencesKey("title_gradient_color_end")
        val ALBUM_GRID_VIEW = booleanPreferencesKey("album_grid_view")
        val ARTIST_GRID_VIEW = booleanPreferencesKey("artist_grid_view")
        val HIDDEN_TABS = stringSetPreferencesKey("hidden_tabs")
        val COVER_DISPLAY_MODE = stringPreferencesKey("cover_display_mode")
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

    val accentColor: Flow<Int?> = data.map { it[Keys.ACCENT_COLOR] }
    val useAlbumArtColor: Flow<Boolean> = data.map { it[Keys.USE_ALBUM_ART_COLOR] ?: true }
    val backgroundUri: Flow<String?> = data.map { it[Keys.BACKGROUND_URI] }
    val defaultWallpaper: Flow<String?> = data.map { it[Keys.DEFAULT_WALLPAPER] }
    val gradientTheme: Flow<String?> = data.map { it[Keys.GRADIENT_THEME] }
    val backgroundBlurEnabled: Flow<Boolean> = data.map { it[Keys.BACKGROUND_BLUR_ENABLED] ?: false }
    val backgroundBlurRadius: Flow<Int> = data.map { it[Keys.BACKGROUND_BLUR_RADIUS] ?: 10 }
    val backgroundScrimAlpha: Flow<Int> = data.map { it[Keys.BACKGROUND_SCRIM_ALPHA] ?: 45 }
    // Gradiente também nos títulos das listas (opcional) — usa as mesmas
    // cores do tema de gradiente ativo (ou "Meia-noite" se o fundo for uma
    // imagem/foto, já que nesse caso não há uma paleta de gradiente ativa).
    val titleGradientEnabled: Flow<Boolean> = data.map { it[Keys.TITLE_GRADIENT_ENABLED] ?: false }
    // Cores do gradiente de título escolhidas livremente pelo usuário (roda de
    // cores, não presets). null nos dois = usa as cores do tema de fundo
    // ativo, como antes — assim quem nunca mexeu nisso não percebe diferença.
    val titleGradientColorStart: Flow<Int?> = data.map { it[Keys.TITLE_GRADIENT_COLOR_START] }
    val titleGradientColorEnd: Flow<Int?> = data.map { it[Keys.TITLE_GRADIENT_COLOR_END] }
    val albumGridView: Flow<Boolean> = data.map { it[Keys.ALBUM_GRID_VIEW] ?: false }
    val artistGridView: Flow<Boolean> = data.map { it[Keys.ARTIST_GRID_VIEW] ?: false }
    /** Nomes das LibraryTab (enum) que o usuário escondeu da barra de abas. */
    val hiddenTabs: Flow<Set<String>> = data.map { it[Keys.HIDDEN_TABS] ?: emptySet() }
    /** "VINYL" | "STATIC" | "FULLSCREEN" — como a capa aparece na tela Agora Tocando. */
    val coverDisplayMode: Flow<String> = data.map { it[Keys.COVER_DISPLAY_MODE] ?: "VINYL" }
    // Padrão "dark", não "system": o app sempre mostra uma imagem de fundo
    // com véu escuro por cima, então texto escuro (o que aconteceria no
    // tema claro do sistema) fica ilegível. Continua possível escolher
    // "light" manualmente se a pessoa realmente quiser.
    val themeMode: Flow<String> = data.map { it[Keys.THEME_MODE] ?: "dark" }
    val ignoredFolders: Flow<Set<String>> = data.map { it[Keys.IGNORED_FOLDERS] ?: emptySet() }
    val crossfadeMs: Flow<Int> = data.map { it[Keys.CROSSFADE_MS] ?: 0 }
    val replayGainEnabled: Flow<Boolean> = data.map { it[Keys.REPLAY_GAIN_ENABLED] ?: false }

    suspend fun setAccentColor(colorArgb: Int) {
        context.dataStore.edit { it[Keys.ACCENT_COLOR] = colorArgb; it[Keys.USE_ALBUM_ART_COLOR] = false }
    }

    /**
     * Remove a cor de destaque customizada, voltando pro rosa fixo do tema
     * padrão "Music Box" (ver [com.harmonic.player.ui.theme.HarmonicTheme]).
     * Sem isso, não existia jeito de "desfazer" [setAccentColor] — então
     * escolher o gradiente padrão (que tem um azul mais saturado que o rosa
     * do ícone) deixava esse azul preso como destaque pra sempre, mesmo
     * depois de trocar de tema e voltar pro padrão.
     */
    suspend fun clearAccentColor() {
        context.dataStore.edit { it.remove(Keys.ACCENT_COLOR); it[Keys.USE_ALBUM_ART_COLOR] = false }
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

    suspend fun setTitleGradientColors(startArgb: Int, endArgb: Int) {
        context.dataStore.edit {
            it[Keys.TITLE_GRADIENT_COLOR_START] = startArgb
            it[Keys.TITLE_GRADIENT_COLOR_END] = endArgb
        }
    }

    /** Volta a usar as cores do tema de fundo ativo em vez de uma cor customizada. */
    suspend fun clearTitleGradientColors() {
        context.dataStore.edit {
            it.remove(Keys.TITLE_GRADIENT_COLOR_START)
            it.remove(Keys.TITLE_GRADIENT_COLOR_END)
        }
    }

    suspend fun setAlbumGridView(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ALBUM_GRID_VIEW] = enabled }
    }

    suspend fun setArtistGridView(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ARTIST_GRID_VIEW] = enabled }
    }

    suspend fun setTabHidden(tabName: String, hidden: Boolean) {
        context.dataStore.edit { prefs ->
            val current = (prefs[Keys.HIDDEN_TABS] ?: emptySet()).toMutableSet()
            if (hidden) current.add(tabName) else current.remove(tabName)
            prefs[Keys.HIDDEN_TABS] = current
        }
    }

    suspend fun setCoverDisplayMode(mode: String) {
        context.dataStore.edit { it[Keys.COVER_DISPLAY_MODE] = mode }
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

    val eqEnabled: Flow<Boolean> = data.map { it[Keys.EQ_ENABLED] ?: false }
    val eqBandLevels: Flow<List<Int>> = data.map { prefs ->
        prefs[Keys.EQ_BAND_LEVELS]?.split(",")?.mapNotNull { it.toIntOrNull() } ?: List(10) { 0 }
    }
    val eqPresetName: Flow<String> = data.map { it[Keys.EQ_PRESET_NAME] ?: "Personalizado" }
    val bassBoostStrength: Flow<Int> = data.map { it[Keys.BASS_BOOST_STRENGTH] ?: 0 }
    val virtualizerStrength: Flow<Int> = data.map { it[Keys.VIRTUALIZER_STRENGTH] ?: 0 }
    val reverbPreset: Flow<Int> = data.map { it[Keys.REVERB_PRESET] ?: 0 }

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

    val sleepTimerEndAt: Flow<Long> = data.map { it[Keys.SLEEP_TIMER_END_AT] ?: 0L }

    suspend fun setSleepTimerEndAt(epochMs: Long) {
        context.dataStore.edit { it[Keys.SLEEP_TIMER_END_AT] = epochMs }
    }
}
