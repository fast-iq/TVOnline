package com.example.tvapp.player

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class GzipResponseTest {

    private fun gzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    private fun readAll(stream: java.io.InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        var n: Int
        while (stream.read(buf).also { n = it } != -1) {
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    @Test
    fun `gzip stream is unwrapped and length unset returned`() {
        val payload = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=550000\nindex_576.m3u8\n"
        val (stream, length) = wrapGzipResponse(ByteArrayInputStream(gzip(payload.toByteArray())), 123L)
        assertEquals(C.LENGTH_UNSET.toLong(), length)
        assertEquals(payload, String(readAll(stream)))
    }

    @Test
    fun `plain stream passes through with original length`() {
        val payload = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=550000\n"
        val (stream, length) = wrapGzipResponse(ByteArrayInputStream(payload.toByteArray()), 42L)
        assertEquals(42L, length)
        assertEquals(payload, String(readAll(stream)))
    }

    @Test
    fun `empty stream passes through`() {
        val (stream, length) = wrapGzipResponse(ByteArrayInputStream(ByteArray(0)), 9L)
        assertEquals(9L, length)
        assertEquals(0, readAll(stream).size)
    }

    @Test
    fun `single byte stream passes through`() {
        val (stream, length) = wrapGzipResponse(ByteArrayInputStream(byteArrayOf(0x1F)), 1L)
        assertEquals(1L, length)
        assertEquals(1, readAll(stream).size)
    }

    @Test
    fun `gzip magic split across read boundary is detected`() {
        val payload = gzip("#EXTM3U\n".toByteArray())
        val (stream, length) = wrapGzipResponse(ByteArrayInputStream(payload), 50L)
        assertEquals(C.LENGTH_UNSET.toLong(), length)
        assertTrue(String(readAll(stream)).startsWith("#EXTM3U"))
    }
}
