package com.example.tvapp.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

/**
 * Оркестрация LimeHD API: авторизация, сборка запросов, разбор ответов.
 * Криптография/UA — в [LimeHdAuth], разбор JSON — в [LimeHdParser],
 * HTTP на HttpURLConnection — в [LimeHdTransport].
 *
 * open + internal швы на HTTP-методы — чтобы юнит-тесты подменяли транспорт
 * заглушками (LimeHdClientFlowTest) без сети.
 */
open class LimeHdClient(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = AppPreferences(appContext)
    private val sessionUuid = UUID.randomUUID().toString()
    private var cachedPublicKey: String? = null
    private var cachedTimeOffset: Long = 0L
    private var cachedRegions: List<LimeHdRegion>? = null
    private var currentRegionCode: Int? = null

    private val userAgent: String by lazy { LimeHdAuth.userAgent(deviceId()) }
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

        /**
         * Базовый URL API. internal var — тестовый шов: юнит-тесты ставят заглушку
         * (несуществующий порт), чтобы не ходить в живую сеть.
         */
        internal var baseUrl = "https://pl.iptv2021.com/"
        private const val INSTALL_TS = 1700000000L
    }

    fun getChannels(): List<Channel> {
        return try {
            ensureAuth()
            val tz = effectiveTz()
            val region = prefs.limehdRegion
            Log.d(TAG, "playlist request: tz=$tz region=$region")
            val url = baseUrl +
                "api/v4/playlist?tz=$tz&region=$region&limit=0&epg=1&epgcnt=4&epg_limit=4" +
                "&installts=$INSTALL_TS&needCategories=0&drm=0"
            val payload = createPayload()
            val rawResponse = httpPostForm(url, "payload=" + URLEncoder.encode(payload, "UTF-8"))
            val body = JSONObject(rawResponse ?: return emptyList())
            val channelsArray = body.optJSONArray("channels") ?: return emptyList()
            LimeHdParser.parseChannels(channelsArray)
        } catch (e: Exception) {
            Log.e(TAG, "getChannels failed: ${e.message}")
            emptyList()
        }
    }

    fun fetchEpg(epgId: String, channelId: String, epgFrom: Int = 0, epgLimit: Int = 1): List<Program> {
        return try {
            val tz = effectiveTz()
            val region = prefs.limehdRegion
            val url = baseUrl +
                "api/v4/epg?id=$epgId&tz=$tz&epg_from=$epgFrom&epg_limit=$epgLimit&grouping=1&region=$region&lang=ru"
            val body = JSONObject(httpGet(url, apiHeaders()) ?: return emptyList())
            LimeHdParser.parseEpg(body, channelId)
        } catch (e: Exception) {
            Log.e(TAG, "fetchEpg failed: ${e.message}")
            emptyList()
        }
    }

    private fun effectiveTz(): Int = prefs.programTimezoneOffset()

    fun getRegions(): List<LimeHdRegion> {
        cachedRegions?.let { return it }
        return try {
            val body = httpGet(baseUrl + "api/v4/config", apiHeaders()) ?: return emptyList()
            val root = JSONObject(body)
            val data = root.optJSONObject("data")
            val regionsArray = root.optJSONArray("regions") ?: data?.optJSONArray("regions")
            val parsed = LimeHdParser.parseRegions(regionsArray)
            if (parsed.isNotEmpty()) {
                cachedRegions = parsed
            }
            val region = root.optJSONObject("region") ?: data?.optJSONObject("region")
            if (region != null) {
                val code = region.optInt("code", 0)
                if (code > 0) currentRegionCode = code
            }
            parsed
        } catch (e: Exception) {
            Log.e(TAG, "getRegions failed: ${e.message}")
            emptyList()
        }
    }

    fun autoRegionCode(): Int? = currentRegionCode

    fun playerRequestHeaders(): Map<String, String> = playerHeaders

    private fun ensureAuth() {
        if (cachedPublicKey != null) return
        try {
            val body = httpGet(baseUrl + "auth", apiHeaders(), true)
            if (body == null) {
                cachedPublicKey = LimeHdAuth.FALLBACK_PUBLIC_KEY
                return
            }
            val key = JSONObject(body).optString("public_key", LimeHdAuth.FALLBACK_PUBLIC_KEY)
            cachedPublicKey = if (key.isBlank()) LimeHdAuth.FALLBACK_PUBLIC_KEY else key
        } catch (e: Exception) {
            cachedPublicKey = LimeHdAuth.FALLBACK_PUBLIC_KEY
            Log.e(TAG, "auth failed: ${e.message}")
        }
    }

    private fun createPayload(): String = LimeHdAuth.buildPayload(deviceId(), System.currentTimeMillis() / 1000L - cachedTimeOffset, cachedPublicKey ?: LimeHdAuth.FALLBACK_PUBLIC_KEY)

    private fun apiHeaders(): Map<String, String> = mapOf(
        "User-Agent" to userAgent,
        "X-LHD-Agent" to userAgent,
        "Accept" to "application/json",
        "Accept-Encoding" to "identity"
    )

    /** Шов теста: GET через [LimeHdTransport] + сверка часов по дате сервера. */
    internal open fun httpGet(url: String, headers: Map<String, String>, updateTimeOffset: Boolean = false): String? {
        val response = LimeHdTransport.get(url, headers)
        if (updateTimeOffset && response.serverDateMs > 0L) {
            cachedTimeOffset = (System.currentTimeMillis() - response.serverDateMs) / 1000L
        }
        return response.body
    }

    /** Шов теста: POST формы через [LimeHdTransport]. */
    internal open fun httpPostForm(url: String, body: String): String? = LimeHdTransport.postForm(url, body, apiHeaders()).body

    private fun deviceId(): String {
        val saved = prefs.limehdDeviceId
        if (!saved.isNullOrBlank()) return saved
        val generated = UUID.randomUUID().toString()
        prefs.limehdDeviceId = generated
        return generated
    }
}
