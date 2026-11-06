package dev.liox.ambermusic.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["albumKey"]),
        Index(value = ["folderPath"]),
        Index(value = ["scanToken"]),
        Index(value = ["title"])
    ]
)
data class SongEntity(
    @PrimaryKey val id: Long,
    val contentUri: String,
    val displayName: String,
    val title: String,
    val artistDisplay: String,
    val albumKey: String,
    val albumTitle: String,
    val albumId: Long?,
    val folderPath: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val modifiedAtSeconds: Long,
    val mimeType: String?,
    val format: String,
    val bitrateBps: Long?,
    val bitrateSource: String,
    val sampleRateHz: Int?,
    val bitDepth: Int?,
    val scanToken: Long,
    @ColumnInfo(defaultValue = "0") val trackNumber: Int = 0,
    @ColumnInfo(defaultValue = "0") val discNumber: Int = 0,
    @ColumnInfo(defaultValue = "1") val isVisible: Boolean = true,
    @ColumnInfo(defaultValue = "''") val genre: String = ""
)

@Entity(
    tableName = "artists",
    indices = [Index(value = ["normalizedName"], unique = true)]
)
data class ArtistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["normalizedName"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlists",
    indices = [Index(value = ["normalizedName"], unique = true)]
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "song_artist_cross_ref",
    primaryKeys = ["songId", "artistId"],
    foreignKeys = [
        ForeignKey(entity = SongEntity::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ArtistEntity::class, parentColumns = ["id"], childColumns = ["artistId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["artistId"])]
)
data class SongArtistCrossRef(val songId: Long, val artistId: Long, val position: Int)

@Entity(
    tableName = "song_category_cross_ref",
    primaryKeys = ["songId", "categoryId"],
    foreignKeys = [
        ForeignKey(entity = SongEntity::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["categoryId"])]
)
data class SongCategoryCrossRef(val songId: Long, val categoryId: Long)

@Entity(
    tableName = "song_playlist_cross_ref",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(entity = SongEntity::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["songId"])]
)
data class SongPlaylistCrossRef(val playlistId: Long, val songId: Long, val position: Int)

@Entity(
    tableName = "song_user_states",
    foreignKeys = [ForeignKey(
        entity = SongEntity::class,
        parentColumns = ["id"],
        childColumns = ["songId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class SongUserStateEntity(
    @PrimaryKey val songId: Long,
    val isFavorite: Boolean = false,
    val playCount: Long = 0,
    val lastPlayedAtEpochMs: Long? = null
)

data class SongWithArtists(
    @Embedded val song: SongEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(SongArtistCrossRef::class, parentColumn = "songId", entityColumn = "artistId")
    )
    val artists: List<ArtistEntity>
)

data class ArtistWithSongs(
    @Embedded val artist: ArtistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(SongArtistCrossRef::class, parentColumn = "artistId", entityColumn = "songId")
    )
    val songs: List<SongEntity>
)

data class CategoryWithSongs(
    @Embedded val category: CategoryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(SongCategoryCrossRef::class, parentColumn = "categoryId", entityColumn = "songId")
    )
    val songs: List<SongEntity>
)

data class PlaylistWithSongs(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(SongPlaylistCrossRef::class, parentColumn = "playlistId", entityColumn = "songId")
    )
    val songs: List<SongEntity>
)

@Entity(
    tableName = "play_events",
    indices = [Index(value = ["songId"]), Index(value = ["playedAtEpochMs"])],
    foreignKeys = [ForeignKey(entity = SongEntity::class,
        parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE)]
)
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val playedAtEpochMs: Long
)

data class AlbumSummary(
    val albumKey: String,
    val albumTitle: String,
    val songCount: Long
)

data class FolderSummary(
    val folderPath: String,
    val songCount: Long
)

/** Non-destructive overrides: never rewrite MP3/FLAC files. */
@Entity(tableName = "song_overrides",
    foreignKeys = [ForeignKey(entity = SongEntity::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE)])
data class SongOverrideEntity(
    @PrimaryKey val songId: Long,
    val title: String,
    val artist: String,
    val album: String
)
