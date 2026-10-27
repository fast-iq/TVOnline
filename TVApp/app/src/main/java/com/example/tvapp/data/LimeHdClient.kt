package com.example.tvapp.data

import android.content.Context
import android.os.Build
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import java.util.UUID
import javax.crypto.Cipher

class LimeHdClient(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = AppPreferences(appContext)
    private val sessionUuid = UUID.randomUUID().toString()
    private var cachedPublicKey: String? = null
    private var cachedTimeOffset: Long = 0L

    private val userAgent: String by lazy { buildUserAgent() }
    private val playerHeaders: Map<String, String> by lazy {
        mapOf(
            "User-Agent" to userAgent,
            "X-LHD-Agent" to userAgent,
            "X-Session-ID" to sessionUuid,
            "Accept-Encoding" to "identity"
        )
    }

    companion object {
        private const val TAG = "LimeHdClient"
        private const val BASE_URL = "https://pl.iptv2021.com/"
        private const val APP_VERSION_NAME = "4.1.2"
        private const val APP_VERSION_CODE = "514"
        private const val INSTALL_TS = 1700000000L
        private const val ORIGINAL_SIGNATURE =
            "909adaa8726a311c84469dc7687245890768cf1a8c6445ad601f1a2c15409321"
        private const val FALLBACK_PUBLIC_KEY =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA27pXRDTT1mshNBVRlvngJ7lb0xcenUL435fNC+arb7rNSTPO1veFrZwJfi+yWRqLdmLGYwol363AFL4zudp3OMIEOeXi2AGgxhaNrr2STRiZnkeu5oLfScWCi0qsMcMlR0pL/F0Xn/k82e1UHUF4U3jlqek0Bn3nWluIrLfUvK51hr2TccrYJGyh5xYRiQatfJW1fTDggvI8/D+y1uhpc3Ef66fYdoy54UbYxkjdP7+xwUeZ7w31h/cX8wDQtoD/wyA82lOJ5ySqbzJyefAitmLqKmrKt21pjS/9DC37E0BU99iBoUZPf4BUulDZphGXk+ob3bnOgXYXwP/v2Ecc7wIDAQAB"
    }

    fun getChannels(): List<Channel> {
        return try {
            ensureAuth()
            val tz = prefs.timezoneOffset
            val region = prefs.limehdRegion
            val url = BASE_URL +
                "api/v4/playlist?tz=$tz&region=$region&limit=0&epg=1&epgcnt=4&epg_limit=4" +
                "&installts=$INSTALL_TS&needCategories=0&drm=0"
            val payload = createPayload()
            val body = JSONObject(httpPostForm(url, "payload=" + URLEncoder.encode(payload, "UTF-8")) ?: return emptyList())
            val channelsArray = body.optJSONArray("channels") ?: return emptyList()
            parseChannels(channelsArray)
        } catch (e: Exception) {
            Log.e(TAG, "getChannels failed: ${e.message}")
            emptyList()
        }
    }

    fun fetchEpg(epgId: String, channelId: String): List<Program> {
        return try {
            val tz = prefs.timezoneOffset
            val region = prefs.limehdRegion
            val url = BASE_URL +
                "api/v4/epg?id=$epgId&tz=$tz&epg_from=-1&epg_limit=3&grouping=1&region=$region&lang=ru"
            val body = JSONObject(httpGet(url, apiHeaders()) ?: return emptyList())
            parseEpg(body, channelId)
        } catch (e: Exception) {
            Log.e(TAG, "fetchEpg failed: ${e.message}")
            emptyList()
        }
    }

    fun playerRequestHeaders(): Map<String, String> = playerHeaders

    private fun parseChannels(array: JSONArray): List<Channel> {
        val entries = mutableListOf<Pair<Int, Channel>>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (!item.optBoolean("available", true)) continue
            if (item.optInt("is_disabled_player", 0) != 0) continue

            val ourId = item.optLong("our_id", -1L)
            if (ourId < 0L) continue

            val stream = item.optJSONObject("stream")
            val streamUrl = firstNonBlank(
                stream?.optString("common"),
                item.optString("url"),
                stream?.optString("cdn"),
                item.optString("foreign_url")
            )
            if (streamUrl == null) continue

            val title = firstNonBlank(
                item.optString("name_ru"),
                item.optString("title"),
                item.optString("name")
            ) ?: continue

            val logoUrl = firstNonBlank(
                item.optString("cached_logo_url"),
                item.optString("image")
            ) ?: ""

            val fallbacks = mutableListOf<String>()
            addUrl(fallbacks, streamUrl)
            addUrl(fallbacks, item.optString("foreign_url"))
            addUrl(fallbacks, stream?.optString("cdn"))
            addUrl(fallbacks, item.optString("url"))

            val current = parseCurrentProgram(item.optJSONArray("current") ?: item.optJSONObject("current"))
            val programs = parseProgramList(item.optJSONArray("current") ?: item.optJSONObject("current"), "limehd_$ourId")
            val fallbackHardcoded = ChannelList.hardcodedChannels.firstOrNull {
                it.name.equals(title, ignoreCase = true) || it.id.lowercase(Locale.US) == ourId.toString()
            }

            val channel = Channel(
                id = "limehd_$ourId",
                name = title,
                logoUrl = logoUrl,
                streamUrl = streamUrl,
                category = fallbackHardcoded?.category ?: if (item.optBoolean("is_federal")) "federal" else "general",
                epgId = ourId.toString(),
                channelImageUrl = logoUrl,
                fallbackStreamUrls = fallbacks.toList(),
                currentProgramTitle = current?.title,
                currentProgramStart = current?.startMs,
                currentProgramEnd = current?.endMs,
                programs = programs,
                isDrm = item.optBoolean("drm", false)
            )

            entries.add(Pair(item.optInt("number", Int.MAX_VALUE), channel))
        }

        return entries.sortedBy { it.first }.map { it.second }
    }

    private fun parseProgramList(value: Any?, channelId: String): List<Program> {
        val items = mutableListOf<JSONObject>()
        when (value) {
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    value.optJSONObject(i)?.let { items.add(it) }
                }
            }
            is JSONObject -> items.add(value)
        }

        val result = mutableListOf<Program>()
        for (item in items) {
            val startSec = item.optLong("timestart", 0L)
            val endSec = item.optLong("timestop", 0L)
            val title = item.optString("title", "")
            if (startSec <= 0L || endSec <= 0L || title.isBlank()) continue
            result.add(
                Program(
                    channelId = channelId,
                    title = title,
                    description = item.optString("desc", "").takeIf { it.isNotBlank() },
                    startTime = startSec * 1000L,
                    endTime = endSec * 1000L,
                    iconUrl = firstNonBlank(item.optString("preview"), item.optString("posterUrl"))
                )
            )
        }
        return result.sortedBy { it.startTime }
    }

    private fun parseCurrentProgram(value: Any?): LimeHdProgram? {
        val obj = when (value) {
            is JSONArray -> value.optJSONObject(0)
            is JSONObject -> value
            else -> null
        } ?: return null

        val title = obj.optString("title", "").takeIf { it.isNotBlank() } ?: return null
        val startSec = obj.optLong("timestart", 0L)
        val endSec = obj.optLong("timestop", 0L)
        return LimeHdProgram(
            title = title,
            startMs = if (startSec > 0L) startSec * 1000L else null,
            endMs = if (endSec > 0L) endSec * 1000L else null
        )
    }

    private fun parseEpg(root: JSONObject, channelId: String): List<Program> {
        val days = root.optJSONArray("epg") ?: return emptyList()
        val result = mutableListOf<Program>()
        for (i in 0 until days.length()) {
            val day = days.optJSONObject(i) ?: continue
            val items = day.optJSONArray("data") ?: continue
            for (j in 0 until items.length()) {
                val item = items.optJSONObject(j) ?: continue
                val startSec = item.optLong("timestart", 0L)
                val endSec = item.optLong("timestop", 0L)
                if (startSec <= 0L || endSec <= 0L) continue
                val title = item.optString("title", "")
                if (title.isBlank()) continue
                result.add(
                    Program(
                        channelId = channelId,
                        title = title,
                        description = item.optString("desc", "").takeIf { it.isNotBlank() },
                        startTime = startSec * 1000L,
                        endTime = endSec * 1000L,
                        iconUrl = firstNonBlank(item.optString("preview"), item.optString("posterUrl"))
                    )
                )
            }
        }
        return result.sortedBy { it.startTime }
    }

    private fun ensureAuth() {
        if (cachedPublicKey != null) return
        try {
            val body = httpGet(BASE_URL + "auth", apiHeaders(), true)
            if (body == null) {
                cachedPublicKey = FALLBACK_PUBLIC_KEY
                return
            }
            val obj = JSONObject(body)
            val key = obj.optString("public_key", FALLBACK_PUBLIC_KEY)
            cachedPublicKey = if (key.isBlank()) FALLBACK_PUBLIC_KEY else key
        } catch (e: Exception) {
            cachedPublicKey = FALLBACK_PUBLIC_KEY
            Log.e(TAG, "auth failed: ${e.message}")
        }
    }

    private fun createPayload(): String {
        val deviceId = deviceId()
        val requestTime = System.currentTimeMillis() / 1000L - cachedTimeOffset
        val json = "{'request_time':$requestTime,'device_id':'$deviceId','signature':'$ORIGINAL_SIGNATURE'}"
        return encrypt(json, cachedPublicKey ?: FALLBACK_PUBLIC_KEY)
    }

    private fun encrypt(data: String, publicKey: String): String {
        val cipher = Cipher.getInstance("RSA/NONE/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, parsePublicKey(publicKey))
        val encoded = cipher.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(encoded, Base64.NO_WRAP)
    }

    private fun parsePublicKey(pem: String): PublicKey {
        val cleaned = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace(" ", "")
        val der = Base64.decode(cleaned, Base64.NO_WRAP)
        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
    }

    private fun apiHeaders(): Map<String, String> = mapOf(
        "User-Agent" to userAgent,
        "X-LHD-Agent" to userAgent,
        "Accept" to "application/json",
        "Accept-Encoding" to "identity"
    )

    private fun httpGet(url: String, headers: Map<String, String>, updateTimeOffset: Boolean = false): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            applyHeaders(connection, headers)
            connection.connectTimeout = 15000
            connection.readTimeout = 25000
            if (updateTimeOffset && connection.date > 0L) {
                cachedTimeOffset = (System.currentTimeMillis() - connection.date) / 1000L
            }
            if (connection.responseCode != 200) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "GET $url failed: ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun httpPostForm(url: String, body: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            applyHeaders(connection, apiHeaders())
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.doOutput = true
            connection.outputStream.use { stream: OutputStream ->
                stream.write(body.toByteArray(StandardCharsets.UTF_8))
            }
            if (connection.responseCode != 200) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "POST $url failed: ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun applyHeaders(connection: HttpURLConnection, headers: Map<String, String>) {
        for ((key, value) in headers) {
            connection.setRequestProperty(key, value)
        }
    }

    private fun deviceId(): String {
        val saved = prefs.limehdDeviceId
        if (!saved.isNullOrBlank()) return saved
        val generated = UUID.randomUUID().toString()
        prefs.limehdDeviceId = generated
        return generated
    }

    private fun buildUserAgent(): String {
        val huawei = Build.MANUFACTURER.lowercase(Locale.US).contains("huawei")
        return "{" +
            "\"platform\":\"android.tv\"," +
            "\"app\":\"limehd.ru.lite\"," +
            "\"version_name\":\"$APP_VERSION_NAME\"," +
            "\"version_code\":\"$APP_VERSION_CODE\"," +
            "\"sdk\":\"${Build.VERSION.SDK_INT}\"," +
            "\"name\":\"${jsonEscape(Build.MODEL)}\"," +
            "\"device_id\":\"${deviceId()}\"," +
            "\"is_huawei\":\"${if (huawei) "1" else "0"}\"" +
            "}"
    }

    private fun jsonEscape(value: String): String = safeHeader(value).replace("\\", "\\\\").replace("\"", "\\\"")

    private fun safeHeader(value: String): String = buildString {
        for (c in value) {
            append(if (c.code in 32..126) c else '?')
        }
    }

    private fun firstNonBlank(vararg values: String?): String? {
        for (value in values) {
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun addUrl(list: MutableList<String>, url: String?) {
        if (url.isNullOrBlank()) return
        if (!list.contains(url)) list.add(url)
    }
}

private data class LimeHdProgram(val title: String?, val startMs: Long?, val endMs: Long?)
