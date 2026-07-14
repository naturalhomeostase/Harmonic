package com.harmonic.player.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Song::class, Playlist::class, PlaylistSongCrossRef::class],
    version = 1,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    companion object {
        @Volatile private var INSTANCE: MusicDatabase? = null

        fun getInstance(context: Context): MusicDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "harmonic_music.db"
                ).build().also { INSTANCE = it }
            }
    }
}
