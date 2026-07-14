package com.harmonic.player.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Representa uma música indexada a partir do MediaStore do Android.
 * `mediaStoreId` é o id original do MediaStore, usado para detectar
 * duplicatas e músicas removidas do aparelho durante o re-scan.
 */
@Entity(tableName = "songs")
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaStoreId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val genre: String?,
    val year: Int?,
    val composer: String?,
    val trackNumber: Int?,
    val durationMs: Long,
    val sizeBytes: Long,
    val path: String,
    val folder: String,
    val bitrate: Int?,
    val sampleRate: Int?,
    val format: String,
    val dateAdded: Long,
    val dateModified: Long,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val playbackPositionMs: Long = 0
)
