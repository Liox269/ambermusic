package dev.liox.ambermusic.data.repository

import dev.liox.ambermusic.data.local.MusicDao
import dev.liox.ambermusic.data.scanner.LocalMusicScanner
import dev.liox.ambermusic.domain.repository.MusicLibraryRepository
import dev.liox.ambermusic.domain.repository.MusicTrack
import dev.liox.ambermusic.domain.repository.ScanSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class RoomMusicLibraryRepository(
    private val dao: MusicDao,
    private val scanner: LocalMusicScanner
) : MusicLibraryRepository {

    override fun observeTracks(): Flow<List<MusicTrack>> = dao.observeSongs()
        .map { songs ->
            songs.map { song ->
                MusicTrack(
                    id = song.id,
                    uri = song.contentUri,
                    title = song.title,
                    artists = song.artistDisplay,
                    album = song.albumTitle,
                    durationMs = song.durationMs,
                    format = song.format,
                    bitrateBps = song.bitrateBps,
                    bitrateSource = song.bitrateSource,
                    sampleRateHz = song.sampleRateHz,
                    bitDepth = song.bitDepth,
                    codecMime = song.codecMime,
                    channelCount = song.channelCount
                )
            }
        }.flowOn(Dispatchers.IO)

    override fun observeTrackCount(): Flow<Int> = dao.observeSongCount().flowOn(Dispatchers.IO)

    override suspend fun scanLocalTracks(): ScanSummary {
        val report = scanner.scan()
        return ScanSummary(
            changed = report.addedOrUpdated,
            unchanged = report.unchanged,
            unsupported = report.unsupported,
            errors = report.errors,
            removed = report.removed,
            complete = report.fullyReconciled
        )
    }
}
