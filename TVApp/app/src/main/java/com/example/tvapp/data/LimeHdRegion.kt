package com.example.tvapp.data

/** Регион LimeHD (код, название, допустимые UTC-смещения). */
data class LimeHdRegion(
    val code: Int,
    val name: String,
    val tzOffsets: List<Int> = emptyList()
)

/** Плоское текстовое представление списка регионов для SharedPreferences. */
internal object RegionsCodec {
    fun encode(regions: List<LimeHdRegion>): String = regions.joinToString("\n") { r ->
        "${r.code}|${r.name}|${r.tzOffsets.joinToString(",")}"
    }

    fun decode(raw: String?): List<LimeHdRegion> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split("\n").mapNotNull { line ->
            val parts = line.split("|", limit = 3)
            if (parts.size < 2) return@mapNotNull null
            val code = parts[0].trim().toIntOrNull() ?: return@mapNotNull null
            if (code <= 0) return@mapNotNull null
            val name = parts[1].trim()
            if (name.isEmpty()) return@mapNotNull null
            val offsets = if (parts.size == 3) {
                parts[2].split(",").mapNotNull { it.trim().toIntOrNull() }
            } else {
                emptyList()
            }
            LimeHdRegion(code = code, name = name, tzOffsets = offsets)
        }
    }
}
