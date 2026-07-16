package com.harmonic.player.data

import android.graphics.Bitmap
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extrai uma cor "vibrante" da capa do álbum pra usar como ambientação na
 * tela "Agora Tocando" — o mesmo efeito que o Spotify/Poweramp fazem, onde
 * cada música tem uma atmosfera de cor levemente diferente baseada na
 * própria capa, em vez de um fundo fixo pra qualquer música.
 */
object PaletteUtil {
    suspend fun extractAccentColor(bitmap: Bitmap): Int? = withContext(Dispatchers.Default) {
        try {
            val palette = Palette.from(bitmap).generate()
            palette.vibrantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
        } catch (e: Exception) {
            null
        }
    }
}
