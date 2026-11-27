package com.example.tvapp.data

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.tvapp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Обновление «внутри» приложения: скачать APK сам и открыть системный
 * установщик. Раньше кидали ACTION_VIEW на URL релиза — ТВ-браузер качал файл
 * в Downloads, показывал «открыть» и на этом останавливался: установщик
 * запускался только вручную из файлового менеджера.
 */
internal object UpdateInstaller {

    /** Скачивает APK (в фоне) и поднимает установщик. [fallbackUrl] — страница релиза для браузера. */
    fun start(activity: Activity, scope: CoroutineScope, apkUrl: String, fallbackUrl: String?) {
        Toast.makeText(activity, R.string.update_downloading, Toast.LENGTH_SHORT).show()
        scope.launch {
            val file = try {
                withContext(Dispatchers.IO) { downloadToCache(activity, apkUrl) }
            } catch (e: Exception) {
                null
            }
            if (activity.isFinishing) return@launch
            if (file == null) {
                Toast.makeText(activity, R.string.update_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            launchInstaller(activity, file, fallbackUrl)
        }
    }

    private fun launchInstaller(activity: Activity, apk: File, fallbackUrl: String?) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(activity, R.string.update_grant_install, Toast.LENGTH_LONG).show()
            try {
                activity.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: ActivityNotFoundException) {
                openBrowser(activity, fallbackUrl)
            }
            return
        }
        try {
            activity.startActivity(installIntent(activity, apk))
        } catch (e: ActivityNotFoundException) {
            openBrowser(activity, fallbackUrl)
        }
    }

    private fun openBrowser(activity: Activity, url: String?) {
        if (url.isNullOrBlank()) {
            Toast.makeText(activity, R.string.update_failed, Toast.LENGTH_LONG).show()
            return
        }
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.update_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun downloadToCache(context: Context, url: String): File {
        val dir = File(context.cacheDir, APK_DIR).apply { mkdirs() }
        val dest = File(dir, APK_NAME)
        dest.delete()
        fetch(URL(url), dest)
        if (dest.length() <= 0L) throw java.io.IOException("empty apk")
        return dest
    }

    private fun fetch(url: URL, dest: File) {
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            val code = connection.responseCode
            if (code in 300..399) {
                // Смена протокола на редиректе (Java сама не идёт) — доводим вручную один раз.
                val location = connection.getHeaderField("Location")
                if (location.isNullOrBlank()) throw java.io.IOException("redirect without Location")
                val next = URL(url, location)
                val redirect = next.openConnection() as HttpURLConnection
                try {
                    redirect.connectTimeout = 15_000
                    redirect.readTimeout = 30_000
                    if (redirect.responseCode != 200) throw java.io.IOException("HTTP ${redirect.responseCode}")
                    redirect.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
                } finally {
                    redirect.disconnect()
                }
                return
            }
            if (code != 200) throw java.io.IOException("HTTP $code")
            connection.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
        } finally {
            connection.disconnect()
        }
    }

    /** content:// URI через FileProvider: direct file:// URI Android 7+ не принимает. */
    internal fun installIntent(context: Context, apk: File): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk),
            "application/vnd.android.package-archive"
        )
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private const val APK_DIR = "updates"
    private const val APK_NAME = "update.apk"
}
