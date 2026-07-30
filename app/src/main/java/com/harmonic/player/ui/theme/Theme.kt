package com.harmonic.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

private val fallbackDarkColors = darkColorScheme(
    primary = Color(0xFFD9A94F),      // dourado do ícone do app (tema padrão "Music Box")
    secondary = Color(0xFFD9A94F),
    tertiary = Color(0xFFD9A94F),
    background = Color(0xFF000000),
    surface = Color(0xFF1A1A1D)
)

private val fallbackLightColors = lightColorScheme(
    primary = Color(0xFF9C6B1E),
    secondary = Color(0xFF9C6B1E),
    tertiary = Color(0xFF9C6B1E)
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
 * 3. Por fim, cai no fallback fixo definido acima (o dourado/bronze do
 *    tema padrão "Music Box").
 */
@Composable
fun HarmonicTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    customAccentColor: Color? = null,
    albumArtSeedColor: Color? = null,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> systemDark
    }

    val fallbackScheme = if (useDark) fallbackDarkColors else fallbackLightColors

    /*
     * ESSA era a causa raiz da cor rosê que insistia em voltar mesmo depois
     * de limpar a cor de destaque e reinstalar o app: em qualquer aparelho
     * Android 12+ (a maioria hoje em dia), sempre que NENHUMA cor de
     * destaque manual estava escolhida — que é exatamente a situação do
     * tema padrão "Music Box" — o app caía nesse branch de Material You
     * dinâmico, que pega a cor automaticamente do PAPEL DE PAREDE DO
     * SISTEMA (não tem nada a ver com o tema escolhido dentro do app). Se
     * o papel de parede do celular tem tons rosados, o Android calcula uma
     * paleta rosada e ela virava a cor de destaque do app inteiro — nome
     * "Music Box", sublinhados, ícones — por baixo do fallback dourado
     * fixo, que na prática nunca era alcançado nesses aparelhos. Isso
     * explica por que nada dentro do app (trocar tema, reinstalar) resolvia:
     * a fonte da cor estava fora do app.
     *
     * O app não tem nenhuma opção de "cor automática do sistema" nas
     * configurações de Aparência — os únicos temas disponíveis usam cores
     * fixas definidas no próprio app — então esse comportamento automático
     * nunca era, de fato, uma escolha do usuário. Removido: agora, sem uma
     * cor manual (ou extraída da capa do álbum), o app sempre usa o
     * dourado/bronze fixo do ícone, como o tema padrão "Music Box" sempre
     * prometeu.
     */
    val baseScheme = when {
        customAccentColor != null -> fallbackScheme.withSingleAccent(customAccentColor)
        albumArtSeedColor != null -> fallbackScheme.withSingleAccent(albumArtSeedColor)
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
