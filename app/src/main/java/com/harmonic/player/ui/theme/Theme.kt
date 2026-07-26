package com.harmonic.player.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

private val fallbackDarkColors = darkColorScheme(
    primary = Color(0xFFE76895),      // rosa do ícone do app (tema padrão "Music Box")
    secondary = Color(0xFFE76895),
    tertiary = Color(0xFFE76895),
    background = Color(0xFF000000),
    surface = Color(0xFF1A1A1D)
)

private val fallbackLightColors = lightColorScheme(
    primary = Color(0xFFC23E74),
    secondary = Color(0xFFC23E74),
    tertiary = Color(0xFFC23E74)
)

enum class ThemeMode { LIGHT, DARK, AMOLED, SYSTEM }

/**
 * Aplica UMA cor de destaque em todos os "papéis" de cor do Material3 que
 * normalmente ficariam roxos por padrão (secondary/tertiary e seus
 * containers) quando só o `primary` é customizado. Sem isso, componentes
 * como o Switch, que usam `secondary`/`tertiary` em vez de `primary` em
 * alguns estados, mostram o roxo padrão do Material Design em vez da cor
 * escolhida pelo usuário.
 */
fun ColorScheme.withSingleAccent(accent: Color): ColorScheme = copy(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent,
    onPrimaryContainer = Color.White,
    secondary = accent,
    onSecondary = Color.White,
    secondaryContainer = accent.copy(alpha = 0.3f),
    onSecondaryContainer = Color.White,
    tertiary = accent,
    onTertiary = Color.White,
    tertiaryContainer = accent.copy(alpha = 0.3f),
    onTertiaryContainer = Color.White,
    inversePrimary = accent
)

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
        customAccentColor != null -> fallbackScheme.withSingleAccent(customAccentColor)
        albumArtSeedColor != null -> fallbackScheme.withSingleAccent(albumArtSeedColor)
        dynamicSupported -> {
            val dynamic = if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            // O Material You dinâmico já é harmonioso por si só (vem do
            // papel de parede do sistema), mas ainda assim unificamos
            // secondary/tertiary com o primary pra manter a promessa de
            // "só uma cor de destaque" em todo o app, sem surpresas.
            dynamic.withSingleAccent(dynamic.primary)
        }
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
