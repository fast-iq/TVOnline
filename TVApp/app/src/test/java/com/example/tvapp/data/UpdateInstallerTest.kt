package com.example.tvapp.data

import android.content.Context
import android.content.Intent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Интент установки скачанного APK: content:// через заявленный FileProvider
 * (file:// Android не принимает), MIME пакета и read-грант для установщика.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateInstallerTest {

    private lateinit var context: Context
    private var previousBaseUrl = ""

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        previousBaseUrl = LimeHdClient.baseUrl
        LimeHdClient.baseUrl = "http://127.0.0.1:9/"
    }

    @After
    fun cleanUp() {
        LimeHdClient.baseUrl = previousBaseUrl
    }

    @Test
    fun installIntentUsesFileProviderContentUriAndPackageMime() {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File(dir, "update.apk").apply { writeBytes(byteArrayOf(0xDE.toByte(), 0xAD.toByte())) }

        val intent = UpdateInstaller.installIntent(context, apk)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("application/vnd.android.package-archive", intent.type)
        assertEquals("com.example.tvapp.fileprovider", intent.data?.authority)
        assertEquals("content", intent.data?.scheme)
        assertTrue(
            "read-грант для установщика обязателен",
            intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
        )
    }
}
