package com.perfectgps.camera.media

import com.perfectgps.camera.model.GeoLocationSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Pins the fingerprint: the exact location is embedded, the image hash binds
 * it to the pixels, and any change to a pixel or to the location breaks
 * verification. The COM segment is metadata, so embedding never touches a
 * pixel and costs no quality.
 */
class PhotoFingerprintTest {

    private fun location(): GeoLocationSnapshot = GeoLocationSnapshot(
            17.123456,
            78.654321,
            12.5,
            543.0,
            2.5,
            90.0,
            1795000000000L,
            "gps",
            8
    )

    private fun minimalJpeg(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0xFF)
        out.write(0xD8)
        out.write(0xFF)
        out.write(0xE0)
        out.write(0x00)
        out.write(0x10)
        out.write("JFIF\u0000".toByteArray(Charsets.UTF_8))
        out.write(byteArrayOf(1, 1, 0, 0, 1, 0, 1, 0, 0))
        out.write(0xFF)
        out.write(0xDA)
        out.write(0x00)
        out.write(0x08)
        out.write(byteArrayOf(1, 1, 0, 0, 0, 63))
        out.write(byteArrayOf(0x12, 0x34, 0x56, 0x78))
        out.write(0xFF)
        out.write(0xD9)
        return out.toByteArray()
    }

    @Test
    fun embedThenExtractKeepsEveryLocationField() {
        val jpeg = minimalJpeg()
        val loc = location()
        val stamped = PhotoFingerprint.embed(jpeg, loc, 1795000001000L)
        assertNotNull(stamped)
        val payload = PhotoFingerprint.extract(stamped)
        assertNotNull(payload)
        payload!!
        assertEquals(17.123456, payload.latitude, 0.0)
        assertEquals(78.654321, payload.longitude, 0.0)
        assertEquals(543.0, payload.altitudeMeters!!, 0.0)
        assertEquals(2.5, payload.speedMetersPerSecond!!, 0.0)
        assertEquals(90.0, payload.bearingDegrees!!, 0.0)
        assertEquals(8, payload.satellites)
        assertEquals(12.5, payload.accuracyMeters, 0.0)
        assertEquals("gps", payload.provider)
        assertEquals(1795000000000L, payload.gpsTimestampMillis)
        assertEquals(1795000001000L, payload.captureTimestampMillis)
        assertEquals(16, payload.dotCodeHex.length)
        assertTrue(payload.fingerprint.isNotEmpty())
    }

    @Test
    fun verifyIsAuthenticForAnUntouchedPhoto() {
        val stamped = PhotoFingerprint.embed(minimalJpeg(), location(), 1795000001000L)
        val result = PhotoFingerprint.verify(stamped)
        assertTrue(result is PhotoFingerprint.Verification.Authentic)
        val payload = (result as PhotoFingerprint.Verification.Authentic).payload
        assertEquals(17.123456, payload.latitude, 0.0)
    }

    @Test
    fun verifyReportsNoFingerprintWhenNoneWasEmbedded() {
        val result = PhotoFingerprint.verify(minimalJpeg())
        assertTrue(result is PhotoFingerprint.Verification.NoFingerprint)
    }

    @Test
    fun verifyDetectsAChangedPixel() {
        val stamped = PhotoFingerprint.embed(minimalJpeg(), location(), 1795000001000L)!!
        // Flip a byte in the scan data (after the SOS segment).
        val tampered = stamped.copyOf()
        tampered[tampered.size - 3] = (tampered[tampered.size - 3].toInt() xor 0xFF).toByte()
        val result = PhotoFingerprint.verify(tampered)
        assertTrue(result is PhotoFingerprint.Verification.Tampered)
    }

    @Test
    fun verifyDetectsAChangedLocation() {
        val stamped = PhotoFingerprint.embed(minimalJpeg(), location(), 1795000001000L)!!
        // Change one hex digit inside the stored image hash in the COM block.
        val text = String(stamped, Charsets.UTF_8)
        val marker = "\"img\":\""
        val at = text.indexOf(marker)
        assertTrue(at >= 0)
        val digitAt = at + marker.length
        val tampered = stamped.copyOf()
        // The COM text starts at byte 6 (after SOI + COM marker + length).
        // Locate the same digit by searching the bytes for the hash prefix.
        val hashStart = indexOf(stamped, "\"img\":\"".toByteArray(Charsets.UTF_8)) +
                "\"img\":\"".length
        tampered[hashStart] = if (tampered[hashStart] == 'a'.code.toByte()) {
            'b'.code.toByte()
        } else {
            'a'.code.toByte()
        }
        val result = PhotoFingerprint.verify(tampered)
        assertTrue(result is PhotoFingerprint.Verification.Tampered)
    }

    @Test
    fun embedWithoutLocationReturnsTheOriginalBytes() {
        val jpeg = minimalJpeg()
        val same = PhotoFingerprint.embed(jpeg, null, 1795000001000L)
        assertTrue(jpeg.contentEquals(same))
        val bad = GeoLocationSnapshot(
                Double.NaN, Double.NaN, 0.0, 0.0, 0.0, 0.0, 0L, "gps", 0
        )
        assertTrue(jpeg.contentEquals(PhotoFingerprint.embed(jpeg, bad, 1795000001000L)))
    }

    @Test
    fun strippingTheFingerprintRestoresTheOriginalBytes() {
        val jpeg = minimalJpeg()
        val stamped = PhotoFingerprint.embed(jpeg, location(), 1795000001000L)!!
        val stripped = PhotoFingerprint.jpegWithoutFingerprint(stamped)
        assertNotNull(stripped)
        assertTrue(jpeg.contentEquals(stripped))
    }

    /**
     * The save path writes EXIF after the photo bytes are produced, and the
     * EXIF write rewrites the JPEG. The fingerprint must therefore be applied
     * to the final bytes, after that rewrite, or verify reports Tampered for
     * a genuine photo. This pins that ordering with a fake APP1 segment
     * standing in for the EXIF segment ExifInterface inserts.
     */
    @Test
    fun verifyAuthenticWhenFingerprintAppliedAfterExifWrite() {
        val withExif = withApp1Segment(minimalJpeg())
        val stamped = PhotoFingerprint.embed(withExif, location(), 1795000001000L)
        val result = PhotoFingerprint.verify(stamped)
        assertTrue(result is PhotoFingerprint.Verification.Authentic)
    }

    @Test
    fun verifyReportsTamperedWhenFingerprintAppliedBeforeExifWrite() {
        val stamped = PhotoFingerprint.embed(minimalJpeg(), location(), 1795000001000L)!!
        val withExif = withApp1Segment(stamped)
        val result = PhotoFingerprint.verify(withExif)
        assertTrue(result is PhotoFingerprint.Verification.Tampered)
    }

    private fun withApp1Segment(jpeg: ByteArray): ByteArray {
        val payload = "Exif\u0000\u0000FAKE".toByteArray(Charsets.UTF_8)
        val length = payload.size + 2
        val out = ByteArray(jpeg.size + 4 + payload.size)
        out[0] = 0xFF.toByte()
        out[1] = 0xD8.toByte()
        out[2] = 0xFF.toByte()
        out[3] = 0xE1.toByte()
        out[4] = ((length ushr 8) and 0xFF).toByte()
        out[5] = (length and 0xFF).toByte()
        System.arraycopy(payload, 0, out, 6, payload.size)
        System.arraycopy(jpeg, 2, out, 6 + payload.size, jpeg.size - 2)
        return out
    }

    @Test
    fun dotCodeIsDeterministicAndChangesWithTime() {
        val loc = location()
        val first = PhotoFingerprint.dotCodeHex(loc, 1795000001000L)
        val again = PhotoFingerprint.dotCodeHex(loc, 1795000001000L)
        assertEquals(first, again)
        val later = PhotoFingerprint.dotCodeHex(loc, 1795000002000L)
        assertNotEquals(first, later)
        assertEquals(16, first.length)
    }

    @Test
    fun payloadJsonRoundTrips() {
        val payload = PhotoFingerprint.buildPayload(
                location(),
                1795000001000L,
                "a".repeat(64),
                "0123456789abcdef"
        )
        val parsed = PhotoFingerprint.payloadFromJson(PhotoFingerprint.payloadToJson(payload))
        assertNotNull(parsed)
        parsed!!
        assertEquals(payload.latitude, parsed.latitude, 0.0)
        assertEquals(payload.longitude, parsed.longitude, 0.0)
        assertEquals(payload.fingerprint, parsed.fingerprint)
        assertEquals(
                PhotoFingerprint.fingerprintOf(payload),
                PhotoFingerprint.fingerprintOf(parsed)
        )
    }

    @Test
    fun extractReturnsNullForAPlainJpeg() {
        assertNull(PhotoFingerprint.extract(minimalJpeg()))
        assertNull(PhotoFingerprint.extract(ByteArray(0)))
        assertNull(PhotoFingerprint.extract(null))
    }

    private fun indexOf(bytes: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..bytes.size - needle.size) {
            for (j in needle.indices) {
                if (bytes[i + j] != needle[j]) {
                    continue@outer
                }
            }
            return i
        }
        return -1
    }
}
