package com.harmonic.player.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    // ---------- Biblioteca ----------

    @Query("SELECT * FROM songs")
    suspend fun getAllSongsOnce(): List<Song>

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' " +
           "OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<Song>>

    @Query("SELECT DISTINCT artist FROM songs ORDER BY artist COLLATE NOCASE ASC")
    fun getArtists(): Flow<List<String>>

    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY album, trackNumber")
    fun getSongsByArtist(artist: String): Flow<List<Song>>

    @Query("SELECT DISTINCT album, albumId FROM songs ORDER BY album COLLATE NOCASE ASC")
    fun getAlbums(): Flow<List<AlbumSummary>>

    @Query("SELECT * FROM songs WHERE albumId = :albumId ORDER BY trackNumber")
    fun getSongsByAlbum(albumId: Long): Flow<List<Song>>

    @Query("SELECT DISTINCT genre FROM songs WHERE genre IS NOT NULL ORDER BY genre ASC")
    fun getGenres(): Flow<List<String>>

    @Query("SELECT * FROM songs WHERE genre = :genre ORDER BY title")
    fun getSongsByGenre(genre: String): Flow<List<Song>>

    @Query("SELECT DISTINCT folder FROM songs ORDER BY folder ASC")
    fun getFolders(): Flow<List<String>>

    @Query("SELECT * FROM songs WHERE folder = :folder ORDER BY title")
    fun getSongsByFolder(folder: String): Flow<List<Song>>

    // ---------- Favoritos / mais tocadas / recentes ----------

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY title")
    fun getFavorites(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE playCount > 0 ORDER BY playCount DESC LIMIT 100")
    fun getMostPlayed(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT 100")
    fun getRecentlyPlayed(): Flow<List<Song>>

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :songId")
    suspend fun setFavorite(songId: Long, isFavorite: Boolean)

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :timestamp WHERE id = :songId")
    suspend fun registerPlay(songId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE songs SET playbackPositionMs = :positionMs WHERE id = :songId")
    suspend fun savePlaybackPosition(songId: Long, positionMs: Long)

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getSongsByIds(ids: List<Long>): List<Song>

    // ---------- Escaneamento ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<Song>)

    @Query("SELECT mediaStoreId FROM songs")
    suspend fun getAllMediaStoreIds(): List<Long>

    @Query("DELETE FROM songs WHERE mediaStoreId IN (:mediaStoreIds)")
    suspend fun deleteByMediaStoreIds(mediaStoreIds: List<Long>)

    // ---------- Playlists ----------

    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getPlaylists(): Flow<List<Playlist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToPlaylist(crossRef: PlaylistSongCrossRef)

    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN playlist_song_cross_ref ON songs.id = playlist_song_cross_ref.songId
        WHERE playlist_song_cross_ref.playlistId = :playlistId
        ORDER BY playlist_song_cross_ref.position ASC
    """)
    fun getPlaylistSongs(playlistId: Long): Flow<List<Song>>

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeFromPlaylist(playlistId: Long, songId: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    // ---------- Marcadores (bookmarks) ----------

    @Insert
    suspend fun insertBookmark(bookmark: Bookmark): Long

    @Query("SELECT * FROM bookmarks WHERE songId = :songId ORDER BY positionMs ASC")
    fun getBookmarksForSong(songId: Long): Flow<List<Bookmark>>

    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmark(bookmarkId: Long)
}

data class AlbumSummary(val album: String, val albumId: Long)
