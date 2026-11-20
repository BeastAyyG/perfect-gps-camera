package com.perfectgps.camera.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the printer-dot grid: the 64-bit code round-trips through the dots,
 * the geometry fits the image, and a missing or altered grid fails to decode
 * instead of returning a wrong code.
 */
class DotPatternTest {

    @Test
    fun bitsAndHexRoundTrip() {
        val codes = listOf(
                "0000000000000000",
                "ffffffffffffffff",
                "0123456789abcdef",
                "deadbeefcafebabe"
        )
        for (code in codes) {
            val bits = DotPattern.bitsFor(code)
            assertEquals(DotPattern.BITS, bits.size)
            assertEquals(code, DotPattern.hexFor(bits))
        }
    }

    @Test
    fun geometryFitsTheImage() {
        for ((width, height) in listOf(1000 to 1000, 4000 to 3000, 3000 to 4000)) {
            val geo = DotPattern.geometry(width, height)
            assertTrue(geo.dotSize >= 3)
            assertTrue(geo.rowY >= 0 && geo.rowY < height)
            assertTrue(geo.startX >= 0)
            val lastX = geo.centerX(DotPattern.TOTAL_POSITIONS - 1)
            assertTrue(lastX < width)
            assertTrue(geo.centerY() in 0 until height)
        }
    }

    @Test
    fun decodeRecoversTheCodeFromSimulatedDots() {
        val code = "0123456789abcdef"
        val width = 4000
        val height = 3000
        val geo = DotPattern.geometry(width, height)
        val bits = DotPattern.bitsFor(code)
        val lit = BooleanArray(DotPattern.TOTAL_POSITIONS)
        for (i in 0 until DotPattern.MARKER_BITS) {
            lit[i] = true
            lit[DotPattern.MARKER_BITS + DotPattern.BITS + i] = true
        }
        for (i in 0 until DotPattern.BITS) {
            lit[DotPattern.MARKER_BITS + i] = bits[i]
        }
        val pixel = { x: Int, y: Int ->
            var on = false
            if (y == geo.centerY()) {
                for (position in 0 until DotPattern.TOTAL_POSITIONS) {
                    if (lit[position] && x == geo.centerX(position)) {
                        on = true
                        break
                    }
                }
            }
            if (on) 0xFF8A939E.toInt() else 0xFF000000.toInt()
        }
        assertEquals(code, DotPattern.decode(width, height, pixel))
    }

    @Test
    fun decodeFailsWhenTheMarkersAreMissing() {
        val width = 4000
        val height = 3000
        val dark = { _: Int, _: Int -> 0xFF000000.toInt() }
        assertNull(DotPattern.decode(width, height, dark))
    }

    @Test
    fun decodeFailsWhenOneMarkerDotIsMissing() {
        val code = "0123456789abcdef"
        val width = 4000
        val height = 3000
        val geo = DotPattern.geometry(width, height)
        val bits = DotPattern.bitsFor(code)
        val lit = BooleanArray(DotPattern.TOTAL_POSITIONS)
        // Start marker present, but the first end-marker dot is missing.
        for (i in 0 until DotPattern.MARKER_BITS) {
            lit[i] = true
        }
        lit[DotPattern.MARKER_BITS + DotPattern.BITS + 1] = true
        lit[DotPattern.MARKER_BITS + DotPattern.BITS + 2] = true
        for (i in 0 until DotPattern.BITS) {
            lit[DotPattern.MARKER_BITS + i] = bits[i]
        }
        val pixel = { x: Int, y: Int ->
            var on = false
            if (y == geo.centerY()) {
                for (position in 0 until DotPattern.TOTAL_POSITIONS) {
                    if (lit[position] && x == geo.centerX(position)) {
                        on = true
                        break
                    }
                }
            }
            if (on) 0xFF8A939E.toInt() else 0xFF000000.toInt()
        }
        assertNull(DotPattern.decode(width, height, pixel))
    }
}
