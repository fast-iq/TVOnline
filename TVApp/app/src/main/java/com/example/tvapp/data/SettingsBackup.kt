package com.example.tvapp.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Сериализация настроек (SharedPreferences) в переносимый JSON и обратно.
 * Каждое значение хранится с явным типом, чтобы при импорте восстановить putInt/putLong/putFloat без потерь.
 */
internal object SettingsBackup {

    fun encode(values: Map<String, *>): String {
        val entries = JSONArray()
        for ((key, value) in values) {
            val entry = JSONObject()
            entry.put("key", key)
            when (value) {
                is String -> entry.put("type", "string").put("value", value)
                is Boolean -> entry.put("type", "bool").put("value", value)
                is Int -> entry.put("type", "int").put("value", value)
                is Long -> entry.put("type", "long").put("value", value)
                is Float -> entry.put("type", "float").put("value", value.toDouble())
                is Set<*> -> {
                    val array = JSONArray()
                    value.filterIsInstance<String>().forEach { array.put(it) }
                    entry.put("type", "set").put("value", array)
                }
                else -> Unit
            }
            if (entry.has("type")) entries.put(entry)
        }
        return JSONObject().put("settings", entries).toString(2)
    }

    fun decode(json: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        val entries = JSONObject(json).optJSONArray("settings") ?: return result
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            val key = entry.optString("key", "")
            if (key.isEmpty()) continue
            when (entry.optString("type", "")) {
                "string" -> result[key] = entry.optString("value", "")
                "bool" -> result[key] = entry.optBoolean("value", false)
                "int" -> result[key] = entry.optInt("value", 0)
                "long" -> result[key] = entry.optLong("value", 0L)
                "float" -> result[key] = entry.optDouble("value", 0.0).toFloat()
                "set" -> {
                    val array = entry.optJSONArray("value") ?: JSONArray()
                    val set = mutableSetOf<String>()
                    for (j in 0 until array.length()) {
                        val item = array.optString(j, null)
                        if (item != null) set.add(item)
                    }
                    result[key] = set
                }
            }
        }
        return result
    }

    fun apply(prefs: android.content.SharedPreferences, values: Map<String, Any>): Boolean {
        if (values.isEmpty()) return false
        val editor = prefs.edit()
        for ((key, value) in values) {
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, value as Set<String>)
                }
            }
        }
        editor.apply()
        return true
    }
}
