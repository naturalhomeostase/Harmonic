package com.harmonic.player.data

import java.io.File

data class LyricLine(val timestampMs: Long, val text: String)

sealed class LyricsResult {
    data class Synced(val lines: List<LyricLine>) : LyricsResult()
    data class PlainText(val text: String) : LyricsResult()
    object NotFound : LyricsResult()
}

/**
 * Letras offline: procura um arquivo com o mesmo nome da música na mesma
 * pasta, na ordem abaixo. Isso é o padrão usado por praticamente todo
 * player de música (Poweramp, VLC etc.) e por ferramentas que baixam letra
 * em lote — então quem já tem letras baixadas de outro player, o Harmonic
 * já lê automaticamente, sem configurar nada.
 *
 *   música.mp3
 *   música.lrc   <- letra sincronizada (linha a linha, com timestamp)
 *   música.txt   <- letra simples (sem sincronismo), usada como fallback
 */
object LyricsRepository {

    fun loadLyrics(song: Song): LyricsResult {
        val audioFile = File(song.path)
        val baseName = audioFile.nameWithoutExtension
        val folder = audioFile.parentFile ?: return LyricsResult.NotFound

        val lrcFile = File(folder, "$baseName.lrc")
        if (lrcFile.exists()) {
            val lines = parseLRC(lrcFile.readText())
            if (lines.isNotEmpty()) return LyricsResult.Synced(lines)
        }

        val txtFile = File(folder, "$baseName.txt")
        if (txtFile.exists()) {
            val text = txtFile.readText().trim()
            if (text.isNotEmpty()) return LyricsResult.PlainText(text)
        }

        return LyricsResult.NotFound
    }

    /**
     * Formato LRC padrão: `[mm:ss.xx]texto da linha`, podendo ter mais de
     * uma tag de tempo por linha (letras "duplicadas" em múltiplos pontos)
     * e metadados como `[ar:Artista]` que são ignorados aqui.
     */
    private fun parseLRC(raw: String): List<LyricLine> {
        val timeTagRegex = Regex("""\[(\d{2}):(\d{2})(?:\.(\d{1,3}))?]""")
        val lines = mutableListOf<LyricLine>()

        raw.lineSequence().forEach { rawLine ->
            val matches = timeTagRegex.findAll(rawLine).toList()
            if (matches.isEmpty()) return@forEach

            val text = rawLine.substring(matches.last().range.last + 1).trim()
            matches.forEach { match ->
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                val timestampMs = minutes * 60_000 + seconds * 1000 + fraction
                lines += LyricLine(timestampMs, text)
            }
        }

        return lines.sortedBy { it.timestampMs }
    }
}
