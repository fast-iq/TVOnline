package com.example.tvapp.player

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Подписи дорожек: язык/каналы/лейблы, нумерация дублей и порядок индексов.
 * В media3 1.11 каждый TrackGroup — один язык/roleFlags, разные дорожки — разные группы.
 */
class TrackLabelsTest {

    private var nextFormatId = 0

    // Format.SelectionFlags.SELECTION_FLAG_FORCED (IntDef-константа недоступна из Kotlin в 1.11).
    private val forcedFlag = 2

    private fun group(vararg formats: Format): Tracks.Group = Tracks.Group(TrackGroup("g$nextFormatId", *formats), true, IntArray(formats.size), BooleanArray(formats.size))

    private fun selectedGroup(selectedIndex: Int, vararg formats: Format): Tracks.Group {
        val status = BooleanArray(formats.size) { it == selectedIndex }
        return Tracks.Group(TrackGroup("g$nextFormatId", *formats), true, IntArray(formats.size), status)
    }

    private fun audioFormat(language: String?, channels: Int, label: String? = null): Format {
        val builder = Format.Builder()
            .setId("a${nextFormatId++}")
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setChannelCount(channels)
            .setSampleRate(48000)
        if (language != null) builder.setLanguage(language)
        if (label != null) builder.setLabel(label)
        return builder.build()
    }

    private fun textFormat(language: String?, selectionFlags: Int = 0, label: String? = null): Format {
        val builder = Format.Builder()
            .setId("t${nextFormatId++}")
            .setSampleMimeType(MimeTypes.APPLICATION_SUBRIP)
            .setSelectionFlags(selectionFlags)
        if (language != null) builder.setLanguage(language)
        if (label != null) builder.setLabel(label)
        return builder.build()
    }

    @Test
    fun duplicateLabelsAreNumbered() {
        val groups = listOf(group(audioFormat(null, 2)), group(audioFormat(null, 2)))

        val labels = TrackLabels.audioLabels(groups, "Дорожка")

        // Одинаковые подписи без метаданных нумеруются, чтобы в диалоге не было близнецов.
        assertEquals(listOf("Stereo · 1", "Stereo · 2"), labels)
    }

    @Test
    fun audioLabelsUseLanguageAndChannelCount() {
        val groups = listOf(group(audioFormat("rus", 2)), group(audioFormat("eng", 6)))

        val labels = TrackLabels.audioLabels(groups, "Дорожка")

        val ru = java.util.Locale("ru").getDisplayLanguage(java.util.Locale.getDefault())
        val en = java.util.Locale("en").getDisplayLanguage(java.util.Locale.getDefault())
        assertEquals(listOf("$ru · Stereo", "$en · 6.ch"), labels)
    }

    @Test
    fun explicitLabelWins() {
        val groups = listOf(group(audioFormat("rus", 2, label = "Комментарий")))

        assertEquals(listOf("Комментарий"), TrackLabels.audioLabels(groups, "Дорожка"))
    }

    @Test
    fun textLabelsMarkForcedAndLanguages() {
        val groups = listOf(group(textFormat("rus")), group(textFormat("eng", selectionFlags = forcedFlag)))

        val labels = TrackLabels.textLabels(groups, "Субтитры")

        val ru = java.util.Locale("ru").getDisplayLanguage(java.util.Locale.getDefault())
        val en = java.util.Locale("en").getDisplayLanguage(java.util.Locale.getDefault())
        assertEquals(listOf(ru, "$en · forced"), labels)
    }

    @Test
    fun filtersOtherTrackTypesAndEmptyGroups() {
        val audio = group(audioFormat("rus", 2))

        assertEquals(emptyList<String>(), TrackLabels.textLabels(listOf(audio), "Субтитры"))
        assertEquals(emptyList<String>(), TrackLabels.audioLabels(emptyList(), "Дорожка"))
    }

    @Test
    fun selectedTrackIndexFollowsSameOrderAsLabels() {
        val none = listOf(group(audioFormat("rus", 2)), group(audioFormat("eng", 2)))
        val secondSelected = listOf(
            selectedGroup(-1, audioFormat("rus", 2)),
            selectedGroup(0, audioFormat("eng", 2))
        )

        assertEquals(-1, TrackLabels.selectedTrackIndex(none))
        assertEquals(1, TrackLabels.selectedTrackIndex(secondSelected))
    }

    @Test
    fun unknownLanguagesFallBackQuietly() {
        val groups = listOf(group(audioFormat("zzz", 0)))

        assertEquals(listOf("Дорожка 1"), TrackLabels.audioLabels(groups, "Дорожка"))
    }
}
