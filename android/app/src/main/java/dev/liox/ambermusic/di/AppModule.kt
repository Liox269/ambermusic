package dev.liox.ambermusic.di

import dev.liox.ambermusic.artwork.ArtworkRepository
import dev.liox.ambermusic.audio.ReplayGainReader
import dev.liox.ambermusic.backup.LibraryBackup
import dev.liox.ambermusic.data.local.MusicDatabase
import dev.liox.ambermusic.data.repository.RoomMusicLibraryRepository
import dev.liox.ambermusic.data.scanner.AudioMetadataExtractor
import dev.liox.ambermusic.data.scanner.LocalMusicScanner
import dev.liox.ambermusic.data.scanner.MusicFolderSettings
import dev.liox.ambermusic.domain.repository.MusicLibraryRepository
import dev.liox.ambermusic.domain.usecase.ScanLocalMusicUseCase
import dev.liox.ambermusic.download.UrlMusicDownloader
import dev.liox.ambermusic.duplicates.DuplicateFinder
import dev.liox.ambermusic.lyrics.LyricsRepository
import dev.liox.ambermusic.playback.AudioRouteTracker
import dev.liox.ambermusic.playback.MusicServiceConnection
import dev.liox.ambermusic.profiles.ProfileStore
import dev.liox.ambermusic.settings.AmberSettings
import dev.liox.ambermusic.smart.SmartPlaylistStore
import dev.liox.ambermusic.viewmodel.MusicViewModel
import dev.liox.ambermusic.visual.ImmersiveBackdrop
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { AmberSettings(androidContext()) }
    single { ProfileStore(androidContext(), get()) }
    single { SmartPlaylistStore(androidContext()) }
    single { DuplicateFinder(androidContext()) }
    single { ReplayGainReader(androidContext()) }
    single { ImmersiveBackdrop(androidContext()) }
    single { LyricsRepository(androidContext()) }
    single { LibraryBackup(get(), get()) }
    single { MusicDatabase.create(androidContext()) }
    single { get<MusicDatabase>().musicDao() }
    single { AudioMetadataExtractor(androidContext()) }
    single { MusicFolderSettings(androidContext()) }
    single { LocalMusicScanner(androidContext(), get(), get(), get()) }
    single { ArtworkRepository(androidContext()) }
    single { AudioRouteTracker(androidContext()) }
    single { MusicServiceConnection(androidContext(), get(), get(), get()) }
    single<MusicLibraryRepository> { RoomMusicLibraryRepository(get(), get()) }
    single { UrlMusicDownloader(androidContext()) }
    factory { ScanLocalMusicUseCase(get()) }
    viewModel { MusicViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}
