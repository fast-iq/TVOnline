package com.example.tvapp.data

import android.util.Log
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * HTTP-транспорт LimeHD на HttpURLConnection. Отделён от [LimeHdClient]:
 * сам клиент ходит в сеть только через его internal-open методы
 * [LimeHdClient.httpGet] / [LimeHdClient.httpPostForm], и юнит-тесты
 * (LimeHdClientFlowTest) подменяют именно их — сюда сеть не доходит.
 */
internal object LimeHdTransport {

    private const val TAG = "LimeHdTransport"

    /**
     * Ответ: тело (null — не 200 или сетевая ошибка) и дата сервера в ms
     * (0 — неизвестна; нужна для коррекции clock-skew в payload).
     */
    class Response(val body: String?, val serverDateMs: Long)

    fun get(url: String, headers: Map<String, String>): Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            applyHeaders(connection, headers)
            connection.connectTimeout = 15_000
            connection.readTimeout = 25_000
            val body = if (connection.responseCode != 200) null else connection.inputStream.bufferedReader().use { it.readText() }
            Response(body, connection.date)
        } catch (e: Exception) {
            Log.e(TAG, "GET $url failed: ${e.message}")
            Response(null, 0L)
        } finally {
            connection.disconnect()
        }
    }

    fun postForm(url: String, body: String, headers: Map<String, String>): Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            applyHeaders(connection, headers)
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.outputStream.use { stream: OutputStream ->
                stream.write(body.toByteArray(StandardCharsets.UTF_8))
            }
            val text = if (connection.responseCode != 200) null else connection.inputStream.bufferedReader().use { it.readText() }
            Response(text, connection.date)
        } catch (e: Exception) {
            Log.e(TAG, "POST $url failed: ${e.message}")
            Response(null, 0L)
        } finally {
            connection.disconnect()
        }
    }

    private fun applyHeaders(connection: HttpURLConnection, headers: Map<String, String>) {
        for ((key, value) in headers) {
            connection.setRequestProperty(key, value)
        }
    }
}
