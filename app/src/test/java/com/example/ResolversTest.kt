package com.example

import com.example.data.remote.JioSaavnDecryptor
import com.example.data.remote.JioSaavnImageResolver
import com.example.data.remote.StreamUrlResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolversTest {

    @Test
    fun testImageResolver() {
        val rawUrl = "http:\\/\\/c.saavncdn.com\\/123\\/Song-150x150.jpg"
        val resolved = JioSaavnImageResolver.resolve(rawUrl, 500)
        assertEquals("https://c.saavncdn.com/123/Song-500x500.jpg", resolved)

        val hdImage = JioSaavnDecryptor.getHdImage(rawUrl)
        assertEquals("https://c.saavncdn.com/123/Song-500x500.jpg", hdImage)

        val html = "Tum Hi Ho &amp; Sunn Raha Hai &quot;Aashiqui 2&#039;s&quot;"
        val unescaped = JioSaavnImageResolver.unescapeHtml(html)
        assertEquals("Tum Hi Ho & Sunn Raha Hai \"Aashiqui 2's\"", unescaped)
    }

    @Test
    fun testQualityTransformation() {
        val url96 = "https://aac.saavncdn.com/123/song_96.mp4"
        val transformed320 = StreamUrlResolver.transformQuality(url96, StreamUrlResolver.AudioQuality.HIGH)
        assertEquals("https://aac.saavncdn.com/123/song_320.mp4", transformed320)

        val fallback160 = StreamUrlResolver.getFallbackUrl(transformed320)
        assertEquals("https://aac.saavncdn.com/123/song_160.mp4", fallback160)

        val fallback96 = StreamUrlResolver.getFallbackUrl(fallback160!!)
        assertEquals("https://aac.saavncdn.com/123/song_96.mp4", fallback96)

        assertNull(StreamUrlResolver.getFallbackUrl(fallback96!!))
    }
}
