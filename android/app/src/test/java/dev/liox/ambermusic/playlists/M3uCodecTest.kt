package dev.liox.ambermusic

import dev.liox.ambermusic.playlists.*
import org.junit.Assert.*
import org.junit.Test

class M3uCodecTest {
    @Test fun roundTripAndUtf8() {
        val library = listOf(LocalTrackPath(1, "Music/Rock", "Canción.flac", "Canción", 125000))
        val text = M3uCodec.export(library)
        assertTrue(text.contains("#EXTM3U"))
        assertEquals(listOf(1L), M3uCodec.match(M3uCodec.parse(text), library).matchedIds)
    }
    @Test fun preservesRepeatedEntriesAndOrdering() {
        val library = listOf(LocalTrackPath(1, "Music/Rock", "Intro.flac", "Intro", 125000))
        val playlist = M3uCodec.parse("#EXTM3U\nMusic/Rock/Intro.flac\nMusic/Rock/Intro.flac\n")
        assertEquals(listOf(1L, 1L), M3uCodec.match(playlist, library).matchedIds)
    }
    @Test fun doNotGuessSameFilename() {
        val tracks = listOf(
            LocalTrackPath(1,"Music/A","Intro.mp3","Intro",5000),
            LocalTrackPath(2,"Music/B","Intro.mp3","Intro",5000))
        val result = M3uCodec.match(listOf(M3uTrack("Intro.mp3")), tracks)
        assertTrue(result.matchedIds.isEmpty())
        assertEquals(1, result.ambiguous.size)
    }
    @Test fun missingPathReported() {
        val result = M3uCodec.match(M3uCodec.parse("#EXTM3U\nmissing.mp3\n"), emptyList())
        assertEquals(listOf("missing.mp3"), result.missing)
    }
    @Test fun parseExtendedAndPlainLines() {
        val tracks = M3uCodec.parse("\uFEFF#EXTM3U\n#EXTINF:180,Track\nMusic/A.mp3\n# comment\nMusic/B.flac\n")
        assertEquals(2, tracks.size)
        assertEquals(180L, tracks[0].durationSeconds)
        assertNull(tracks[1].title)
    }
}
