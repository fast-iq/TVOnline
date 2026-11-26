package com.example.tvapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun parseReleaseExtractsAllFields() {
        val json = """
            {
              "tag_name": "v1.2.3",
              "html_url": "https://github.com/fast-iq/TVOnline/releases/tag/v1.2.3",
              "body": "Fixes and features",
              "assets": [
                { "name": "source.zip", "browser_download_url": "https://example.com/source.zip" },
                { "name": "app.apk", "browser_download_url": "https://example.com/app-release.apk" }
              ]
            }
        """.trimIndent()
        val info = UpdateChecker.parseRelease(json)
        assertEquals("1.2.3", info?.version)
        assertEquals("https://github.com/fast-iq/TVOnline/releases/tag/v1.2.3", info?.url)
        assertEquals("Fixes and features", info?.notes)
        assertEquals("https://example.com/app-release.apk", info?.apkUrl)
    }

    @Test
    fun parseReleaseWithoutAssetsAndBody() {
        val info = UpdateChecker.parseRelease("""{"tag_name":"2.0","html_url":"u"}""")
        assertEquals("2.0", info?.version)
        assertNull(info?.apkUrl)
        assertEquals("", info?.notes)
    }

    @Test
    fun parseReleaseRejectsBadInput() {
        assertNull(UpdateChecker.parseRelease("not json"))
        assertNull(UpdateChecker.parseRelease("""{"tag_name":""}"""))
        assertNull(UpdateChecker.parseRelease("""{}"""))
    }

    @Test
    fun newerVersionDetection() {
        assertTrue(UpdateChecker.isNewerVersion("1.1.0", "1.0.9"))
        assertTrue(UpdateChecker.isNewerVersion("1.10.0", "1.9.5"))
        assertTrue(UpdateChecker.isNewerVersion("2.0.0", "1.99.99"))
        assertTrue(UpdateChecker.isNewerVersion("1.2.4", "1.2.3.9"))
        assertTrue(UpdateChecker.isNewerVersion("v1.2.4", "v1.2.3"))
    }

    @Test
    fun equalAndOlderVersionsAreNotNewer() {
        assertFalse(UpdateChecker.isNewerVersion("1.2.3", "1.2.3"))
        assertFalse(UpdateChecker.isNewerVersion("V1.2.3", "v1.2.3"))
        assertFalse(UpdateChecker.isNewerVersion("1.2.2", "1.2.3"))
        assertFalse(UpdateChecker.isNewerVersion("1.2", "1.2.0"))
    }

    @Test
    fun nonNumericPartsTreatedAsZero() {
        assertTrue(UpdateChecker.isNewerVersion("1.2.beta", "1.1.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.2.beta", "1.2.1"))
    }
}
