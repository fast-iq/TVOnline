package com.example.tvapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionsCodecTest {

    @Test
    fun roundTripPreservesCodeNameAndTz() {
        val regions = listOf(
            LimeHdRegion(77, "Москва", listOf(3)),
            LimeHdRegion(66, "Свердловская область", listOf(5, 6)),
            LimeHdRegion(1, "Без таймзон")
        )
        val decoded = RegionsCodec.decode(RegionsCodec.encode(regions))
        assertEquals(regions, decoded)
    }

    @Test
    fun decodeSkipsGarbageAndKeepsValid() {
        val raw = "77|Москва|3\nbad line\n0|Нулевой|3\n55||3\n66|Свердловская|5,x,6"
        val decoded = RegionsCodec.decode(raw)
        assertEquals(2, decoded.size)
        assertEquals(77, decoded[0].code)
        assertEquals(listOf(5, 6), decoded[1].tzOffsets)
    }

    @Test
    fun decodeEmptyInputsReturnEmpty() {
        assertTrue(RegionsCodec.decode(null).isEmpty())
        assertTrue(RegionsCodec.decode("").isEmpty())
        assertEquals(emptyList<LimeHdRegion>(), RegionsCodec.encode(emptyList()).let { RegionsCodec.decode(it) })
    }
}
