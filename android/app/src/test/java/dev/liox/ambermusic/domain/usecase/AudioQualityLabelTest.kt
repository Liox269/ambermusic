package dev.liox.ambermusic.domain.usecase

import dev.liox.ambermusic.domain.repository.MusicTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioQualityLabelTest {
    private fun track(format: String, mime: String, bps: Long?, source: String,
                      sampleRate: Int?, bitDepth: Int?) = MusicTrack(
        id = 4, uri = "content://test/4", title = "Test", artists = "Test", album = "Test",
        durationMs = 120_000, format = format, bitrateBps = bps, bitrateSource = source,
        sampleRateHz = sampleRate, bitDepth = bitDepth, codecMime = mime, channelCount = 2
    )

    @Test fun flacShowsTrueResolution() {
        assertEquals("HI-RES 24-BIT 96KHZ FLAC", AudioQualityLabel.forTrack(
            track("FLAC", "audio/flac", null, "UNKNOWN", 96000, 24)))
    }

    @Test fun standardResolutionFlacDoesNotClaimHiRes() {
        assertEquals("16-BIT 44.1KHZ FLAC", AudioQualityLabel.forTrack(
            track("FLAC", "audio/flac", null, "UNKNOWN", 44100, 16)))
    }

    @Test fun compressedCodecsAreIdentifiedByActualMime() {
        assertEquals("~192KBPS 48KHZ OPUS", AudioQualityLabel.forTrack(
            track("OGG", "audio/opus", 192000, "ESTIMATED", 48000, null)))
        assertEquals("256KBPS 44.1KHZ AAC", AudioQualityLabel.forTrack(
            track("M4A", "audio/mp4a-latm", 256000, "RETRIEVER", 44100, null)))
    }

    @Test fun unknownTechnicalDataAreNotInvented() {
        assertEquals("MP3", AudioQualityLabel.forTrack(
            track("MP3", "audio/mpeg", null, "UNKNOWN", null, null)))
        assertTrue(AudioQualityLabel.bitrateDescription(192000, "ESTIMATED")
            .contains("estimación"))
    }
}
