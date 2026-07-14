package com.harmonic.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.harmonic.player.data.DefaultWallpaper
import com.harmonic.player.data.SettingsRepository

/**
 * Desenha o papel de parede escolhido pelo usuário (um dos 5 padrões
 * embutidos, ou uma imagem própria) atrás de toda a navegação, com um véu
 * escuro semi-transparente por cima pra garantir contraste do texto — sem
 * isso, telas com fotos muito claras ficariam ilegíveis.
 *
 * Aplicado uma única vez, no topo da árvore de composição: cada tela usa
 * `containerColor = Color.Transparent` no Scaffold pra deixar esse fundo
 * aparecer, em vez de cada tela desenhar seu próprio fundo (o que causaria
 * repetição de código e possível dessincronia entre telas).
 */
@Composable
fun AppBackground(settings: SettingsRepository, content: @Composable () -> Unit) {
    val defaultWallpaperName by settings.defaultWallpaper.collectAsState(initial = null)
    val customBackgroundUri by settings.backgroundUri.collectAsState(initial = null)

    Box(modifier = Modifier.fillMaxSize()) {
        val model: Any? = when {
            customBackgroundUri != null -> customBackgroundUri
            defaultWallpaperName != null ->
                DefaultWallpaper.values().find { it.name == defaultWallpaperName }
                    ?.let { "file:///android_asset/${it.assetPath}" }
            else -> null
        }

        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
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
