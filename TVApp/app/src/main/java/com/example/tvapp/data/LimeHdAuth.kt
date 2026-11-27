package com.example.tvapp.data

import android.os.Build
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import javax.crypto.Cipher

/**
 * Криптография протокола LimeHD: зашифрованный payload плейлиста и псевдо-
 * User-Agent приложения. Отделена от [LimeHdClient] (оркестрация+транспорт),
 * чтобы протокольные константы жили в одном месте.
 */
internal object LimeHdAuth {

    /** Ключ из APK: используется, если /auth недоступен или прислал мусор. */
    const val FALLBACK_PUBLIC_KEY =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA27pXRDTT1mshNBVRlvngJ7lb0xcenUL435fNC+arb7rNSTPO1veFrZwJfi+yWRqLdmLGYwol363AFL4zudp3OMIEOeXi2AGgxhaNrr2STRiZnkeu5oLfScWCi0qsMcMlR0pL/F0Xn/k82e1UHUF4U3jlqek0Bn3nWluIrLfUvK51hr2TccrYJGyh5xYRiQatfJW1fTDggvI8/D+y1uhpc3Ef66fYdoy54UbYxkjdP7+xwUeZ7w31h/cX8wDQtoD/wyA82lOJ5ySqbzJyefAitmLqKmrKt21pjS/9DC37E0BU99iBoUZPf4BUulDZphGXk+ob3bnOgXYXwP/v2Ecc7wIDAQAB"

    private const val APP_VERSION_NAME = "4.1.2"
    private const val APP_VERSION_CODE = "514"
    private const val ORIGINAL_SIGNATURE =
        "909adaa8726a311c84469dc7687245890768cf1a8c6445ad601f1a2c15409321"

    /** Шифрует payload плейлиста; бросает исключение при кривом ключе — решает вызывающий. */
    fun buildPayload(deviceId: String, requestTimeSec: Long, publicKey: String): String {
        val json = "{'request_time':$requestTimeSec,'device_id':'$deviceId','signature':'$ORIGINAL_SIGNATURE'}"
        return encrypt(json, publicKey)
    }

    /** JSON-«юзер-агент», который limehd-lite шлёт вместо обычного UA. */
    fun userAgent(deviceId: String): String {
        val huawei = Build.MANUFACTURER.lowercase(Locale.US).contains("huawei")
        return "{" +
            "\"platform\":\"android.tv\"," +
            "\"app\":\"limehd.ru.lite\"," +
            "\"version_name\":\"$APP_VERSION_NAME\"," +
            "\"version_code\":\"$APP_VERSION_CODE\"," +
            "\"sdk\":\"${Build.VERSION.SDK_INT}\"," +
            "\"name\":\"${jsonEscape(Build.MODEL)}\"," +
            "\"device_id\":\"${deviceId}\"," +
            "\"is_huawei\":\"${if (huawei) "1" else "0"}\"" +
            "}"
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

    private fun jsonEscape(value: String): String = safeHeader(value).replace("\\", "\\\\").replace("\"", "\\\"")

    private fun safeHeader(value: String): String = buildString {
        for (c in value) {
            append(if (c.code in 32..126) c else '?')
        }
    }
}
