package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Song
import com.example.player.LyricsManager
import com.example.util.AlbumArtHelper
import com.example.util.SettingsManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context verifies app name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Soundbox", appName)
    }

    @Test
    fun `lyrics manager parses LRC timestamps correctly`() {
        val lrc = """
            [00:10.50]Hello world
            [00:20.00]Soundbox Music Player
        """.trimIndent()
        val lines = LyricsManager.parseLyrics(lrc, 180000L)
        assertEquals(2, lines.size)
        assertEquals(10500L, lines[0].timeMs)
        assertEquals("Hello world", lines[0].text)
        assertEquals(20000L, lines[1].timeMs)
        assertEquals("Soundbox Music Player", lines[1].text)
    }

    @Test
    fun `lyrics manager cleans video noise from title and artist`() {
        val cleanedTitle = LyricsManager.cleanString("Cheap Thrills (Official Video) [1080p HQ].mp3", isArtist = false)
        assertTrue("Cleaned title should not contain official video noise", !cleanedTitle.contains("official video", ignoreCase = true))
        assertTrue("Cleaned title should contain Cheap Thrills", cleanedTitle.contains("Cheap Thrills"))
    }

    @Test
    fun `lyrics manager extracts artist and title from hyphenated filename`() {
        val song = Song(
            id = "test1",
            title = "Alan Walker - Faded (Official Audio)",
            artist = "<unknown>",
            album = "Unknown",
            duration = 200000L,
            path = "/storage/music/Alan Walker - Faded (Official Audio).mp3",
            size = 5000000L,
            folderPath = "/storage/music",
            folderName = "music"
        )
        val metadata = LyricsManager.extractArtistAndTitle(song)
        assertTrue("Artist should be extracted as Alan Walker", metadata.artist.contains("alan walker", ignoreCase = true))
        assertTrue("Title should be extracted as Faded", metadata.title.contains("faded", ignoreCase = true))
    }

    @Test
    fun `album art helper resolves deterministic vector artwork`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resIdJazz = AlbumArtHelper.getAlbumArtResId("Jazz Unplugged", "Miles Davis", "Jazz", "song123", context)
        assertEquals(R.drawable.ic_art_vinyl_pulse, resIdJazz)

        val resIdElectronic = AlbumArtHelper.getAlbumArtResId("Cyber Synthwave", "Master Synth", "Electronic", "song456", context)
        assertEquals(R.drawable.ic_art_cyber_synth, resIdElectronic)
    }

    @Test
    fun `settings manager initializes with expected defaults`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsManager.getInstance(context)
        assertNotNull(settings)
        assertTrue(settings.lockscreenPlayerEnabled.value)
    }
}

