package com.harmonic.player.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Carrega a capa do álbum embutida no próprio arquivo de áudio.
 *
 * No Android 10+ (API 29+), o jeito certo é `ContentResolver.loadThumbnail`
 * direto na URI da música — é a API que o próprio Google recomenda pra
 * scoped storage, e funciona pra praticamente qualquer formato com arte
 * embutida (MP3/ID3, FLAC, M4A...).
 *
 * Em versões mais antigas, usamos a URI legada `content://media/.../albumart`,
 * que ainda funciona bem antes do scoped storage existir.
 */
object AlbumArtLoader {

    suspend fun load(context: Context, song: Song, sizePx: Int = 512): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.mediaStoreId)
                context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
            } else {
                val legacyUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), song.albumId
                )
                context.contentResolver.openInputStream(legacyUri)?.use { BitmapFactory.decodeStream(it) }
            }
        } catch (e: Exception) {
            // Nem toda música tem capa embutida — retorna null e a UI cai
            // pro placeholder, sem travar nada.
            null
        }
    }
}
