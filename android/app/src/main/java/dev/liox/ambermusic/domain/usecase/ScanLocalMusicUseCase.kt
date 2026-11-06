package dev.liox.ambermusic.domain.usecase

import dev.liox.ambermusic.domain.repository.MusicLibraryRepository
import dev.liox.ambermusic.domain.repository.ScanSummary

class ScanLocalMusicUseCase(private val repository: MusicLibraryRepository) {
    suspend operator fun invoke(): ScanSummary = repository.scanLocalTracks()
}
