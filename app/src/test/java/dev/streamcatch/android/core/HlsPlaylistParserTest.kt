package dev.streamcatch.android.core

import org.junit.Assert.*
import org.junit.Test

class HlsPlaylistParserTest {
    private val root = "https://video.example/media/master.m3u8?auth=token"

    @Test fun masterVariantsOrderByBandwidthAndResolveRelativeUris() {
        val text = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=440000,RESOLUTION=640x360,CODECS="avc1.42e01e,mp4a.40.2"
            low/playlist.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2"
            ../high/playlist.m3u8
        """.trimIndent()
        val variants = (HlsPlaylistParser.parse(root, text) as HlsPlaylist.Master).variants
        assertEquals(2, variants.size)
        assertEquals("1920x1080", variants.first().resolution)
        assertEquals("avc1.640028,mp4a.40.2", variants.first().codecs)
        assertEquals("https://video.example/high/playlist.m3u8", variants.first().url)
        assertEquals("https://video.example/media/low/playlist.m3u8", variants.last().url)
    }

    @Test fun vodSegmentsAndQueryStrings() {
        val text = """
            #EXTM3U
            #EXT-X-TARGETDURATION:4
            #EXTINF:4.0,
            seg001.ts?token=x
            #EXTINF:4.0,
            ./seg002.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val list = HlsPlaylistParser.parse(root, text) as HlsPlaylist.Video
        assertEquals(2, list.segments.size)
        assertEquals("https://video.example/media/seg001.ts?token=x", list.segments[0])
    }

    private fun refuses(contents: String) {
        try {
            HlsPlaylistParser.parse(root, contents.trimIndent())
            fail("Should reject unsupported stream")
        } catch (_: UnsupportedHlsException) {
            // expected explicit failure, not silent corruption
        }
    }

    @Test fun refusesLivePlaylists() = refuses(
        """
            #EXTM3U
            #EXTINF:5,
            seg001.ts
        """
    )

    @Test fun refusesEncryptedPlaylists() = refuses(
        """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="keys/private"
            #EXTINF:5,
            seg001.ts
            #EXT-X-ENDLIST
        """
    )

    @Test fun refusesFragmentedMp4() = refuses(
        """
            #EXTM3U
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:5,
            part001.m4s
            #EXT-X-ENDLIST
        """
    )

    @Test fun refusesSeparateAudioMaster() = refuses(
        """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="English",URI="audio.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=3000000,AUDIO="audio"
            video.m3u8
        """
    )

    @Test fun refusesByteRanges() = refuses(
        """
            #EXTM3U
            #EXT-X-BYTERANGE:1000@0
            #EXTINF:5,
            seg.ts
            #EXT-X-ENDLIST
        """
    )

    @Test fun rejectsInsecureSegmentUris() {
        try {
            HlsPlaylistParser.parse(root, """
                #EXTM3U
                #EXTINF:5,
                http://example.com/segment.ts
                #EXT-X-ENDLIST
            """.trimIndent())
            fail("HTTP not allowed")
        } catch (_: UnsupportedHlsException) {
        }
    }

    @Test fun rejectsInvalidPlaylistHeader() {
        try {
            HlsPlaylistParser.parse(root, "not a playlist")
            fail("Expected invalid header")
        } catch (_: IllegalArgumentException) {
        }
    }
}