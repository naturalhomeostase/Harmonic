package com.harmonic.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.harmonic.player.data.DefaultWallpaper
import com.harmonic.player.data.SettingsRepository

/**
 * Desenha o papel de parede escolhido pelo usuário (um dos padrões
 * embutidos, uma foto própria escolhida da galeria) atrás de toda a
 * navegação, com um véu escuro semi-transparente por cima pra garantir
 * contraste do texto, e blur opcional.
 *
 * Aplicado uma única vez, no topo da árvore de composição: cada tela usa
 * `containerColor = Color.Transparent` no Scaffold, e cada item de lista
 * usa fundo transparente, pra deixar esse fundo aparecer atrás de tudo em
 * vez de cada elemento desenhar seu próprio fundo opaco por cima.
 *
 * Nota: o blur (`Modifier.blur`) só tem efeito real no Android 12+ (API 31+)
 * — em aparelhos mais antigos a chamada não quebra nada, só não borra a
 * imagem, já que a API de blur nativo do Compose depende do RenderEffect
 * do sistema, que não existe em versões anteriores.
 */
@Composable
fun AppBackground(settings: SettingsRepository, content: @Composable () -> Unit) {
    val defaultWallpaperName by settings.defaultWallpaper.collectAsState(initial = null)
    val customBackgroundUri by settings.backgroundUri.collectAsState(initial = null)
    val blurEnabled by settings.backgroundBlurEnabled.collectAsState(initial = false)

    Box(modifier = Modifier.fillMaxSize()) {
        val model: Any? = when {
            customBackgroundUri != null -> customBackgroundUri
            defaultWallpaperName != null ->
                DefaultWallpaper.values().find { it.name == defaultWallpaperName }
                    ?.let { "file:///android_asset/${it.assetPath}" }
            // Enquanto o usuário não escolhe nada, mostra um dos wallpapers
            // inclusos por padrão — uma tela preta lisa no primeiro uso
            // pareceria um bug, não uma escolha de design.
            else -> "file:///android_asset/${DefaultWallpaper.LION_FIRE.assetPath}"
        }

        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (blurEnabled) Modifier.blur(28.dp) else Modifier)
            )
            // Véu escuro pra garantir legibilidade do texto sobre a imagem
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )
        }

        content()
    }
}
