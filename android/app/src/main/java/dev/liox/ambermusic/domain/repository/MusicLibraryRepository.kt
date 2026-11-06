package dev.liox.ambermusic.domain.repository

import kotlinx.coroutines.flow.Flow

data class MusicTrack(
    val id: Long,
    val uri: String,
    val title: String,
    val artists: String,
    val album: String,
    val durationMs: Long,
    val format: String,
    val bitrateBps: Long?,
    val bitrateSource: String,
    val sampleRateHz: Int?,
    val bitDepth: Int?
)

data class ScanSummary(
    val changed: Int,
    val unchanged: Int,
    val unsupported: Int,
    val errors: Int,
    val removed: Int,
    val complete: Boolean
)

interface MusicLibraryRepository {
    fun observeTracks(): Flow<List<MusicTrack>>
    fun observeTrackCount(): Flow<Int>
    suspend fun scanLocalTracks(): ScanSummary
}
