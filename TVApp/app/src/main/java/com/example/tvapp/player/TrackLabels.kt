package com.example.tvapp.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks

/**
 * Подписи аудио-/субтитровых дорожек для диалогов выбора. Чистая логика
 * (никакого Context): формат дорожки -> человекочитаемая строка.
 */
object TrackLabels {

    private const val SELECTION_FLAG_FORCED = 2

    /**
     * Подписи всех треков аудиогрупп. [fallbackPrefix] — основа подписи без
     * метаданных («Дорожка» → «Дорожка 2»); канал 5.1+ помечается по числу каналов.
     */
    fun audioLabels(groups: List<Tracks.Group>, fallbackPrefix: String): List<String> {
        val labels = mutableListOf<String>()
        var n = 1
        for (group in groups.filter { it.type == C.TRACK_TYPE_AUDIO }) {
            for (i in 0 until group.length) {
                labels.add(describeAudio(group.getTrackFormat(i), "$fallbackPrefix $n"))
                n++
            }
        }
        return disambiguate(labels, fallbackPrefix)
    }

    /** Подписи всех треков субтитровых групп. */
    fun textLabels(groups: List<Tracks.Group>, fallbackPrefix: String): List<String> {
        val labels = mutableListOf<String>()
        var n = 1
        for (group in groups.filter { it.type == C.TRACK_TYPE_TEXT }) {
            for (i in 0 until group.length) {
                labels.add(describeText(group.getTrackFormat(i), "$fallbackPrefix $n"))
                n++
            }
        }
        return disambiguate(labels, fallbackPrefix)
    }

    /** Одинаковые подписи (например, два «Stereo» без метаданных) нумеруем, кроме уже пронумерованных fallback'ов. */
    private fun disambiguate(labels: List<String>, fallbackPrefix: String): List<String> {
        val counts = labels.groupingBy { it }.eachCount()
        return labels.mapIndexed { i, label ->
            if (counts.getValue(label) > 1 && !label.startsWith(fallbackPrefix)) "$label · ${i + 1}" else label
        }
    }

    internal fun describeAudio(format: Format, fallback: String): String {
        format.label?.takeIf { it.isNotBlank() }?.let { return it }
        val language = displayLanguage(format.language)
        val channels = when {
            format.channelCount <= 0 -> null
            format.channelCount == 2 -> "Stereo"
            format.channelCount == 1 -> "Mono"
            else -> "${format.channelCount}.ch"
        }
        val parts = listOfNotNull(language, channels)
        return parts.joinToString(" · ").ifBlank { fallback }
    }

    internal fun describeText(format: Format, fallback: String): String {
        format.label?.takeIf { it.isNotBlank() }?.let { return it }
        val language = displayLanguage(format.language) ?: return fallback
        // Значение Format.SelectionFlags.SELECTION_FLAG_FORCED (IntDef-константа
        // недоступна из Kotlin как поле в 1.11).
        return if (format.selectionFlags and SELECTION_FLAG_FORCED != 0) {
            "$language · forced"
        } else {
            language
        }
    }

    /** Индекс выбранного трека (0-базированный, порядок обхода как в *Labels); -1 — «авто»/нет выбора. */
    fun selectedTrackIndex(groups: List<Tracks.Group>): Int {
        var n = 0
        for (group in groups) {
            for (i in 0 until group.length) {
                if (group.isTrackSelected(i)) return n
                n++
            }
        }
        return -1
    }

    /** ISO-639-2/B -> ISO-639-1: потоки отдают трёхбуквенные коды, ICU понимает только двухбуквенные. */
    private val ISO3_TO_ISO2 = mapOf(
        "rus" to "ru", "eng" to "en", "ukr" to "uk", "bel" to "be", "tur" to "tr",
        "fra" to "fr", "deu" to "de", "spa" to "es", "ita" to "it", "pol" to "pl",
        "chi" to "zh", "ara" to "ar", "hin" to "hi", "kaz" to "kk", "uzb" to "uz",
        "aze" to "az", "lav" to "lv", "lit" to "lt", "est" to "et", "fin" to "fi",
        "swe" to "sv", "nor" to "no", "dan" to "da", "dut" to "nl", "gre" to "el",
        "heb" to "he", "arm" to "hy", "geo" to "ka"
    )

    /** ISO-639 код (двух- или трёхбуквенный) -> отображаемое название языка. */
    internal fun displayLanguage(code: String?): String? {
        val trimmed = code?.trim()?.lowercase()?.takeIf { it.length == 2 || it.length == 3 } ?: return null
        val two = when (trimmed.length) {
            2 -> trimmed
            else -> ISO3_TO_ISO2[trimmed]
        } ?: return null
        return try {
            java.util.Locale(two).getDisplayLanguage(java.util.Locale.getDefault())
                .takeIf { it.isNotBlank() && !it.equals(two, ignoreCase = true) }
        } catch (e: Exception) {
            null
        }
    }
}
