package dev.liox.ambermusic.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.util.Locale

@Dao
abstract class MusicDao {

    @Upsert
    abstract suspend fun upsertSong(song: SongEntity)

    @Query("SELECT * FROM songs WHERE id = :songId LIMIT 1")
    abstract suspend fun getSong(songId: Long): SongEntity?

    @Query("UPDATE songs SET scanToken = :scanToken, isVisible = 1 WHERE id = :songId")
    abstract suspend fun markSongSeen(songId: Long, scanToken: Long)

    @Query("UPDATE songs SET scanToken = :scanToken, isVisible = 1, dateAddedSeconds = :addedAt WHERE id = :songId")
    abstract suspend fun markSongSeenWithDate(songId: Long, scanToken: Long, addedAt: Long)

    @Query("UPDATE songs SET isVisible = 0 WHERE scanToken != :scanToken AND isVisible = 1")
    abstract suspend fun hideSongsNotSeenInScan(scanToken: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertArtistIgnore(artist: ArtistEntity): Long

    @Query("SELECT id FROM artists WHERE normalizedName = :normalizedName LIMIT 1")
    abstract suspend fun artistIdByNormalizedName(normalizedName: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSongArtist(ref: SongArtistCrossRef)

    @Query("DELETE FROM song_artist_cross_ref WHERE songId = :songId")
    abstract suspend fun deleteSongArtists(songId: Long)

    @Query("DELETE FROM artists WHERE id NOT IN (SELECT artistId FROM song_artist_cross_ref)")
    abstract suspend fun pruneOrphanArtists(): Int

    @Transaction
    open suspend fun saveScannedSong(song: SongEntity, artistNames: List<String>) {
        upsertSong(song)
        deleteSongArtists(song.id)
        artistNames.distinctBy { it.lowercase(Locale.ROOT) }.forEachIndexed { position, name ->
            val key = name.trim().lowercase(Locale.ROOT)
            if (key.isNotEmpty()) {
                val inserted = insertArtistIgnore(ArtistEntity(name = name.trim(), normalizedName = key))
                val artistId = if (inserted >= 0L) inserted else artistIdByNormalizedName(key)
                if (artistId != null) {
                    insertSongArtist(SongArtistCrossRef(song.id, artistId, position))
                }
            }
        }
    }

    @Transaction
    open suspend fun finishFullScan(scanToken: Long): Int {
        return hideSongsNotSeenInScan(scanToken)
    }

    @Query("SELECT COUNT(*) FROM songs WHERE isVisible = 1 AND durationMs > 60000")
    abstract fun observeSongCount(): Flow<Int>

    @Query("SELECT * FROM songs WHERE isVisible = 1 AND durationMs > 60000 ORDER BY title COLLATE NOCASE, id")
    abstract fun observeSongs(): Flow<List<SongEntity>>

    @Transaction
    @Query("SELECT * FROM songs WHERE id = :songId LIMIT 1")
    abstract fun observeSongWithArtists(songId: Long): Flow<SongWithArtists?>

    @Query("SELECT a.* FROM artists a INNER JOIN song_artist_cross_ref sa ON sa.artistId = a.id WHERE sa.songId = :songId ORDER BY sa.position")
    abstract fun observeOrderedSongArtists(songId: Long): Flow<List<ArtistEntity>>

    @Transaction
    @Query("SELECT * FROM artists WHERE id IN (SELECT sa.artistId FROM song_artist_cross_ref sa INNER JOIN songs s ON s.id = sa.songId WHERE s.isVisible = 1 AND s.durationMs > 60000) ORDER BY name COLLATE NOCASE")
    abstract fun observeArtistsWithSongs(): Flow<List<ArtistWithSongs>>

    @Transaction
    @Query("SELECT * FROM artists WHERE id = :artistId LIMIT 1")
    abstract fun observeArtistWithSongs(artistId: Long): Flow<ArtistWithSongs?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertCategoryIgnore(category: CategoryEntity): Long

    @Query("SELECT id FROM categories WHERE normalizedName = :key LIMIT 1")
    abstract suspend fun categoryIdByName(key: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSongCategory(ref: SongCategoryCrossRef)

    @Query("DELETE FROM song_category_cross_ref WHERE songId = :songId AND categoryId = :categoryId")
    abstract suspend fun removeSongFromCategory(songId: Long, categoryId: Long)

    @Query("DELETE FROM categories WHERE id = :categoryId")
    abstract suspend fun deleteCategory(categoryId: Long)

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    abstract fun observeCategories(): Flow<List<CategoryEntity>>

    @Transaction
    @Query("SELECT * FROM categories WHERE id = :categoryId LIMIT 1")
    abstract fun observeCategoryWithSongs(categoryId: Long): Flow<CategoryWithSongs?>

    @Query("SELECT s.* FROM songs s INNER JOIN song_category_cross_ref sc ON sc.songId = s.id WHERE sc.categoryId = :categoryId AND s.isVisible = 1 AND s.durationMs > 60000 ORDER BY s.title COLLATE NOCASE")
    abstract fun observeSongsInCategory(categoryId: Long): Flow<List<SongEntity>>

    @Transaction
    open suspend fun createCategory(name: String): Long {
        val clean = name.trim().requireNonBlank()
        val key = clean.lowercase(Locale.ROOT)
        val inserted = insertCategoryIgnore(CategoryEntity(name = clean, normalizedName = key))
        return if (inserted >= 0L) inserted else requireNotNull(categoryIdByName(key))
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertPlaylistIgnore(playlist: PlaylistEntity): Long

    @Query("SELECT id FROM playlists WHERE normalizedName = :key LIMIT 1")
    abstract suspend fun playlistIdByName(key: String): Long?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM song_playlist_cross_ref WHERE playlistId = :playlistId")
    abstract suspend fun nextPlaylistPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSongPlaylist(ref: SongPlaylistCrossRef)

    @Query("DELETE FROM song_playlist_cross_ref WHERE songId = :songId AND playlistId = :playlistId")
    abstract suspend fun removeSongFromPlaylist(songId: Long, playlistId: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    abstract suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE")
    abstract fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    abstract fun observePlaylistWithSongs(playlistId: Long): Flow<PlaylistWithSongs?>

    @Query("SELECT s.* FROM songs s INNER JOIN song_playlist_cross_ref sp ON sp.songId = s.id WHERE sp.playlistId = :playlistId AND s.isVisible = 1 AND s.durationMs > 60000 ORDER BY sp.position, s.id")
    abstract fun observePlaylistSongsOrdered(playlistId: Long): Flow<List<SongEntity>>

    @Transaction
    open suspend fun createPlaylist(name: String): Long {
        val clean = name.trim().requireNonBlank()
        val key = clean.lowercase(Locale.ROOT)
        val inserted = insertPlaylistIgnore(PlaylistEntity(name = clean, normalizedName = key))
        return if (inserted >= 0L) inserted else requireNotNull(playlistIdByName(key))
    }

    @Transaction
    open suspend fun appendSongToPlaylist(playlistId: Long, songId: Long) {
        insertSongPlaylist(SongPlaylistCrossRef(playlistId, songId, nextPlaylistPosition(playlistId)))
    }

    @Query("SELECT albumKey, albumTitle, COUNT(*) AS songCount FROM songs WHERE isVisible = 1 AND durationMs > 60000 GROUP BY albumKey, albumTitle ORDER BY albumTitle COLLATE NOCASE")
    abstract fun observeAlbums(): Flow<List<AlbumSummary>>

    @Query("SELECT * FROM songs WHERE isVisible = 1 AND durationMs > 60000 AND albumKey = :albumKey ORDER BY discNumber, CASE WHEN trackNumber = 0 THEN 9999 ELSE trackNumber END, title COLLATE NOCASE")
    abstract fun observeAlbumSongs(albumKey: String): Flow<List<SongEntity>>

    @Query("SELECT folderPath, COUNT(*) AS songCount FROM songs WHERE isVisible = 1 AND durationMs > 60000 GROUP BY folderPath ORDER BY folderPath COLLATE NOCASE")
    abstract fun observeFolders(): Flow<List<FolderSummary>>

    @Query("SELECT * FROM songs WHERE isVisible = 1 AND durationMs > 60000 AND folderPath = :folderPath ORDER BY title COLLATE NOCASE")
    abstract fun observeFolderSongs(folderPath: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM song_category_cross_ref ORDER BY categoryId, songId")
    abstract suspend fun categoryLinks(): List<SongCategoryCrossRef>

    @Query("SELECT * FROM song_playlist_cross_ref ORDER BY playlistId, position")
    abstract suspend fun playlistLinks(): List<SongPlaylistCrossRef>

    @Query("SELECT * FROM song_user_states")
    abstract fun observeUserStates(): Flow<List<SongUserStateEntity>>

    @Query("SELECT * FROM song_user_states WHERE isFavorite = 1")
    abstract suspend fun favoriteRows(): List<SongUserStateEntity>

    @Query("SELECT s.* FROM songs s INNER JOIN song_user_states u ON u.songId = s.id WHERE s.isVisible = 1 AND s.durationMs > 60000 AND u.playCount > 0 ORDER BY u.playCount DESC, u.lastPlayedAtEpochMs DESC LIMIT 50")
    abstract fun observeMostPlayedSongs(): Flow<List<SongEntity>>

    @Query("SELECT s.* FROM songs s INNER JOIN song_user_states u ON u.songId = s.id WHERE s.isVisible = 1 AND s.durationMs > 60000 AND u.lastPlayedAtEpochMs IS NOT NULL ORDER BY u.lastPlayedAtEpochMs DESC LIMIT 50")
    abstract fun observeRecentlyPlayedSongs(): Flow<List<SongEntity>>

    @Query("UPDATE songs SET title = :title, artistDisplay = :artist, albumTitle = :album WHERE id = :songId")
    abstract suspend fun updateDisplayMetadata(songId: Long, title: String, artist: String, album: String)

    @Query("SELECT * FROM song_overrides WHERE songId = :songId LIMIT 1")
    abstract suspend fun getOverride(songId: Long): SongOverrideEntity?

    @Upsert
    abstract suspend fun saveOverride(override: SongOverrideEntity)

    @Transaction
    open suspend fun saveDisplayOverride(songId: Long, title: String, artist: String, album: String) {
        require(title.isNotBlank() && artist.isNotBlank() && album.isNotBlank())
        saveOverride(SongOverrideEntity(songId, title.trim(), artist.trim(), album.trim()))
        updateDisplayMetadata(songId, title.trim(), artist.trim(), album.trim())
        deleteSongArtists(songId)
        dev.liox.ambermusic.data.scanner.ArtistNameParser.parse(artist).forEachIndexed { index, name ->
            val key = name.trim().lowercase(Locale.ROOT)
            val inserted = insertArtistIgnore(ArtistEntity(name = name, normalizedName = key))
            val artistId = if (inserted >= 0L) inserted else artistIdByNormalizedName(key)
            if (artistId != null) insertSongArtist(SongArtistCrossRef(songId, artistId, index))
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertUserStateIgnore(state: SongUserStateEntity)

    @Query("UPDATE song_user_states SET isFavorite = :favorite WHERE songId = :songId")
    abstract suspend fun updateFavorite(songId: Long, favorite: Boolean)

    @Transaction
    open suspend fun setFavorite(songId: Long, favorite: Boolean) {
        insertUserStateIgnore(SongUserStateEntity(songId))
        updateFavorite(songId, favorite)
    }

    @Query("SELECT songId FROM song_user_states WHERE isFavorite = 1")
    abstract fun observeFavoriteIds(): Flow<List<Long>>

    @Query("SELECT * FROM song_user_states WHERE songId = :songId LIMIT 1")
    abstract suspend fun getUserState(songId: Long): SongUserStateEntity?

    @Query("SELECT s.* FROM songs s INNER JOIN song_user_states u ON s.id = u.songId WHERE u.isFavorite = 1 AND s.isVisible = 1 AND s.durationMs > 60000 ORDER BY s.title COLLATE NOCASE")
    abstract fun observeFavoriteSongs(): Flow<List<SongEntity>>

    @Insert
    abstract suspend fun insertPlayEvent(event: PlayEventEntity)

    @Query("SELECT COUNT(*) FROM play_events WHERE playedAtEpochMs >= :sinceMs")
    abstract fun observePlayCountSince(sinceMs: Long): Flow<Long>

    @Query("""SELECT s.* FROM songs s INNER JOIN play_events pe ON pe.songId = s.id
        WHERE pe.playedAtEpochMs >= :sinceMs AND s.isVisible = 1 AND s.durationMs > 60000
        GROUP BY s.id ORDER BY COUNT(pe.id) DESC, MAX(pe.playedAtEpochMs) DESC LIMIT 20""")
    abstract fun observeTopSince(sinceMs: Long): Flow<List<SongEntity>>

    @Query("UPDATE song_user_states SET playCount = playCount + 1, lastPlayedAtEpochMs = :whenMs WHERE songId = :songId")
    abstract suspend fun incrementPlayCount(songId: Long, whenMs: Long)

    @Transaction
    open suspend fun recordPlay(songId: Long, whenMs: Long = System.currentTimeMillis()) {
        insertUserStateIgnore(SongUserStateEntity(songId))
        incrementPlayCount(songId, whenMs)
        insertPlayEvent(PlayEventEntity(songId = songId, playedAtEpochMs = whenMs))
    }
}

private fun String.requireNonBlank(): String {
    require(isNotBlank()) { "El nombre no puede estar vacío" }
    return this
}
