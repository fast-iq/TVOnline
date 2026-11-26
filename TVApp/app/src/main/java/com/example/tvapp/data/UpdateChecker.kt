package com.example.tvapp.data

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version: String,
    val url: String,
    val notes: String,
    val apkUrl: String?
)

sealed class UpdateResult {
    data class Available(val info: UpdateInfo) : UpdateResult()
    object UpToDate : UpdateResult()
    object Failed : UpdateResult()
}

object UpdateChecker {

    private const val REPO = "fast-iq/TVOnline"
    private const val RELEASES_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000

    fun checkDetailed(context: Context): UpdateResult {
        val json = fetchLatestRelease() ?: return UpdateResult.Failed
        val info = parseRelease(json) ?: return UpdateResult.Failed
        return if (isNewerVersion(info.version, currentVersion(context))) {
            UpdateResult.Available(info)
        } else {
            UpdateResult.UpToDate
        }
    }

    fun check(context: Context): UpdateInfo? = (checkDetailed(context) as? UpdateResult.Available)?.info

    fun currentVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }

    private fun fetchLatestRelease(): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(RELEASES_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "TVOnline-app")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    internal fun parseRelease(json: String): UpdateInfo? = try {
        val obj = JSONObject(json)
        val tag = obj.optString("tag_name", "")
        if (tag.isBlank()) {
            null
        } else {
            val assets = obj.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val downloadUrl = asset.optString("browser_download_url", "")
                    if (downloadUrl.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = downloadUrl
                        break
                    }
                }
            }
            UpdateInfo(
                version = normalizeVersion(tag),
                url = obj.optString("html_url", ""),
                notes = obj.optString("body", ""),
                apkUrl = apkUrl
            )
        }
    } catch (e: Exception) {
        null
    }

    internal fun normalizeVersion(tag: String): String = tag.trim().removePrefix("v").removePrefix("V")

    internal fun isNewerVersion(latest: String, current: String): Boolean {
        val a = normalizeVersion(latest).split(".")
        val b = normalizeVersion(current).split(".")
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val x = a.getOrNull(i)?.toIntOrNull() ?: 0
            val y = b.getOrNull(i)?.toIntOrNull() ?: 0
            if (x != y) return x > y
        }
        return false
    }
}
