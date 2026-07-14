package com.harmonic.player.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

private val fallbackDarkColors = darkColorScheme(
    primary = Color(0xFFFFB74D),      // laranja (combina com o wallpaper do leão)
    secondary = Color(0xFF80DEEA),
    background = Color(0xFF0E0E10),
    surface = Color(0xFF1A1A1D)
)

private val fallbackLightColors = lightColorScheme(
    primary = Color(0xFFEF6C00),
    secondary = Color(0xFF00838F)
)

enum class ThemeMode { LIGHT, DARK, AMOLED, SYSTEM }

/**
 * Tema principal do Harmonic.
 *
 * Prioridade de cores:
 * 1. Se o usuário escolheu uma cor de destaque manual -> usa ela sobre o
 *    esquema claro/escuro base (funciona em qualquer versão do Android).
 * 2. Senão, se houver uma cor extraída da capa do álbum -> usa ela do
 *    mesmo jeito.
 * 3. Senão, no Android 12+, usa Material You dinâmico (cor do papel de
 *    parede do SISTEMA — diferente do papel de parede do app).
 * 4. Por fim, cai no fallback fixo definido acima.
 */
@Composable
fun HarmonicTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    customAccentColor: Color? = null,
    albumArtSeedColor: Color? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> systemDark
    }

    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val fallbackScheme = if (useDark) fallbackDarkColors else fallbackLightColors

    val baseScheme = when {
        customAccentColor != null -> fallbackScheme.copy(primary = customAccentColor)
        albumArtSeedColor != null -> fallbackScheme.copy(primary = albumArtSeedColor)
        dynamicSupported -> if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> fallbackScheme
    }

    val colorScheme = if (themeMode == ThemeMode.AMOLED) {
        baseScheme.copy(background = Color.Black, surface = Color.Black)
    } else baseScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HarmonicTypography,
        content = content
    )
}

/** Utilitário para converter uma Color do Compose num Int ARGB (usado ao salvar no DataStore). */
fun Color.toArgbInt(): Int = this.toArgb()
