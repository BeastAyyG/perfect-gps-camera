package com.perfectgps.camera.media

import android.os.Build
import com.perfectgps.camera.model.GeoLocationSnapshot
import java.security.MessageDigest

/**
 * Tamper-proof fingerprint for a photo, in the style of the invisible tracking
 * dots colour laser printers embed in every page.
 *
 * Every photo taken with a location gets two linked marks:
 *
 * 1. A hidden data block (this file): the exact location, in full detail, plus a
 *    SHA-256 fingerprint of the image itself, embedded as a JPEG COM segment.
 *    The segment is metadata, not pixels, so it does not touch the image and
 *    costs zero quality. Changing any pixel, or any part of the location, breaks
 *    the fingerprint, so the photo itself proves where it was taken.
 * 2. A printer-dot grid ([DotPattern]): tiny dots drawn into the photo that
 *    encode a short code derived from the same location and time. The code in
 *    the dots matches the code in the data block, so the two marks cross-check.
 *
 * Verification ([verify]) recomputes both hashes from the file and reports
 * whether the photo and its location are exactly as captured.
 */
object PhotoFingerprint {
    const val MARKER = "PGC-FP1"
    const val APP_NAME = "Perfect GPS Camera"

    private const val COM = 0xFFFE
    private const val SOI = 0xFFD8
    private const val SOS = 0xFFDA

    /**
     * The full detail carried by the fingerprint. Every field that describes
     * where and when the photo was taken, plus the hashes that bind it to the
     * image.
     */
    class Payload(
            val latitude: Double,
            val longitude: Double,
            val altitudeMeters: Double?,
            val speedMetersPerSecond: Double?,
            val bearingDegrees: Double?,
            val satellites: Int,
            val accuracyMeters: Double,
            val provider: String,
            val gpsTimestampMillis: Long,
            val captureTimestampMillis: Long,
            val make: String,
            val model: String,
            val imageHash: String,
            val dotCodeHex: String,
            val fingerprint: String
    )

    sealed class Verification {
        class Authentic(val payload: Payload) : Verification()
        object NoFingerprint : Verification()
        object Tampered : Verification()
    }

    /**
     * The 8-byte code the dot grid encodes, as 16 hex characters. It is the
     * first 8 bytes of SHA-256 over the location and capture time, so it is
     * fixed for a given photo and matches the code stored in the data block.
     */
    @JvmStatic
    fun dotCodeHex(location: GeoLocationSnapshot, captureTimeMillis: Long): String {
        val input = location.latitude().toString() + "|" +
                location.longitude().toString() + "|" +
                location.timestampMillis().toString() + "|" +
                captureTimeMillis.toString()
        return sha256Hex(input.toByteArray(Charsets.UTF_8)).substring(0, 16)
    }

    /**
     * Embeds the fingerprint data block into a JPEG. Returns the new JPEG
     * bytes, or the original bytes when there is no valid location or anything
     * fails. The image pixels are never touched, so quality is unchanged.
     */
    @JvmStatic
    fun embed(
            jpeg: ByteArray?,
            location: GeoLocationSnapshot?,
            captureTimeMillis: Long
    ): ByteArray? {
        if (jpeg == null || jpeg.isEmpty()) {
            return jpeg
        }
        if (location == null || !location.isValid()) {
            return jpeg
        }
        return try {
            val imageHash = sha256Hex(jpeg)
            val dotCode = dotCodeHex(location, captureTimeMillis)
            val payload = buildPayload(location, captureTimeMillis, imageHash, dotCode)
            val json = payloadToJson(payload)
            insertComSegment(jpeg, MARKER + ":" + json)
        } catch (error: Exception) {
            jpeg
        }
    }

    /** Reads the fingerprint data block out of a JPEG, or null if none. */
    @JvmStatic
    fun extract(jpeg: ByteArray?): Payload? {
        if (jpeg == null || jpeg.isEmpty()) {
            return null
        }
        return try {
            val text = findComText(jpeg) ?: return null
            if (!text.startsWith(MARKER + ":")) {
                return null
            }
            payloadFromJson(text.substring(MARKER.length + 1))
        } catch (error: Exception) {
            null
        }
    }

    /**
     * Verifies a JPEG. Authentic only when the stored image hash matches the
     * image bytes (with the data block itself removed) and the stored
     * fingerprint matches the stored fields. Any change to a pixel, or to any
     * part of the location, returns Tampered.
     */
    @JvmStatic
    fun verify(jpeg: ByteArray?): Verification {
        val payload = extract(jpeg) ?: return Verification.NoFingerprint
        if (jpeg == null) {
            return Verification.NoFingerprint
        }
        return try {
            val stripped = jpegWithoutFingerprint(jpeg) ?: return Verification.Tampered
            if (sha256Hex(stripped) != payload.imageHash) {
                return Verification.Tampered
            }
            if (fingerprintOf(payload) != payload.fingerprint) {
                return Verification.Tampered
            }
            Verification.Authentic(payload)
        } catch (error: Exception) {
            Verification.Tampered
        }
    }

    @JvmStatic
    fun buildPayload(
            location: GeoLocationSnapshot,
            captureTimeMillis: Long,
            imageHash: String,
            dotCodeHex: String
    ): Payload {
        val altitude = if (location.hasAltitude()) location.altitudeMeters() else null
        val speed = if (location.speedMetersPerSecond().isFinite() &&
                location.speedMetersPerSecond() >= 0.0) {
            location.speedMetersPerSecond()
        } else {
            null
        }
        val bearing = if (location.bearingDegrees().isFinite() &&
                location.bearingDegrees() >= 0.0 &&
                location.bearingDegrees() < 360.0) {
            location.bearingDegrees()
        } else {
            null
        }
        val payload = Payload(
                latitude = location.latitude(),
                longitude = location.longitude(),
                altitudeMeters = altitude,
                speedMetersPerSecond = speed,
                bearingDegrees = bearing,
                satellites = maxOf(location.satellites(), 0),
                accuracyMeters = location.accuracyMeters(),
                provider = location.provider(),
                gpsTimestampMillis = location.timestampMillis(),
                captureTimestampMillis = captureTimeMillis,
                make = safe(Build.MANUFACTURER),
                model = safe(Build.MODEL),
                imageHash = imageHash,
                dotCodeHex = dotCodeHex,
                fingerprint = ""
        )
        return Payload(
                latitude = payload.latitude,
                longitude = payload.longitude,
                altitudeMeters = payload.altitudeMeters,
                speedMetersPerSecond = payload.speedMetersPerSecond,
                bearingDegrees = payload.bearingDegrees,
                satellites = payload.satellites,
                accuracyMeters = payload.accuracyMeters,
                provider = payload.provider,
                gpsTimestampMillis = payload.gpsTimestampMillis,
                captureTimestampMillis = payload.captureTimestampMillis,
                make = payload.make,
                model = payload.model,
                imageHash = payload.imageHash,
                dotCodeHex = payload.dotCodeHex,
                fingerprint = fingerprintOf(payload)
        )
    }

    /** Canonical string the fingerprint hash is computed over. Fixed order. */
    @JvmStatic
    fun fingerprintOf(payload: Payload): String {
        val input = MARKER + "|" +
                payload.latitude.toString() + "|" +
                payload.longitude.toString() + "|" +
                num(payload.altitudeMeters) + "|" +
                num(payload.speedMetersPerSecond) + "|" +
                num(payload.bearingDegrees) + "|" +
                payload.satellites.toString() + "|" +
                payload.accuracyMeters.toString() + "|" +
                payload.provider + "|" +
                payload.gpsTimestampMillis.toString() + "|" +
                payload.captureTimestampMillis.toString() + "|" +
                payload.make + "|" +
                payload.model + "|" +
                payload.imageHash + "|" +
                payload.dotCodeHex
        return sha256Hex(input.toByteArray(Charsets.UTF_8))
    }

    @JvmStatic
    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val out = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            if (v < 0x10) {
                out.append('0')
            }
            out.append(Integer.toHexString(v))
        }
        return out.toString()
    }

    @JvmStatic
    fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

    private fun num(value: Double?): String = value?.toString() ?: "null"

    private fun safe(value: String?): String =
            value?.replace('\u0000', ' ')?.trim()?.ifEmpty { "Unknown" } ?: "Unknown"

    // -- JSON (minimal, flat objects only; no org.json, so unit tests can run) --

    @JvmStatic
    fun payloadToJson(payload: Payload): String {
        val out = StringBuilder()
        out.append('{')
        field(out, "v", "1", true)
        field(out, "lat", payload.latitude.toString(), false)
        field(out, "lon", payload.longitude.toString(), false)
        field(out, "alt", num(payload.altitudeMeters), false)
        field(out, "spd", num(payload.speedMetersPerSecond), false)
        field(out, "brg", num(payload.bearingDegrees), false)
        field(out, "sat", payload.satellites.toString(), false)
        field(out, "acc", payload.accuracyMeters.toString(), false)
        field(out, "prov", payload.provider, true)
        field(out, "gpsTs", payload.gpsTimestampMillis.toString(), false)
        field(out, "capTs", payload.captureTimestampMillis.toString(), false)
        field(out, "make", payload.make, true)
        field(out, "model", payload.model, true)
        field(out, "img", payload.imageHash, true)
        field(out, "dots", payload.dotCodeHex, true)
        field(out, "fp", payload.fingerprint, true)
        out.append('}')
        return out.toString()
    }

    private fun field(out: StringBuilder, key: String, value: String, quoted: Boolean) {
        if (out.length > 1) {
            out.append(',')
        }
        out.append('"').append(key).append("\":")
        if (quoted) {
            out.append('"').append(escape(value)).append('"')
        } else {
            out.append(value)
        }
    }

    private fun escape(value: String): String {
        val out = StringBuilder(value.length)
        for (c in value) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> {
                    if (c.code < 0x20) {
                        out.append(String.format("\\u%04x", c.code))
                    } else {
                        out.append(c)
                    }
                }
            }
        }
        return out.toString()
    }

    @JvmStatic
    fun payloadFromJson(json: String): Payload? {
        val lat = number(json, "lat") ?: return null
        val lon = number(json, "lon") ?: return null
        val acc = number(json, "acc") ?: return null
        val prov = text(json, "prov") ?: return null
        val gpsTs = long(json, "gpsTs") ?: return null
        val capTs = long(json, "capTs") ?: return null
        val img = text(json, "img") ?: return null
        val dots = text(json, "dots") ?: return null
        val fp = text(json, "fp") ?: return null
        val sat = long(json, "sat")?.toInt() ?: 0
        return Payload(
                latitude = lat,
                longitude = lon,
                altitudeMeters = nullableNumber(json, "alt"),
                speedMetersPerSecond = nullableNumber(json, "spd"),
                bearingDegrees = nullableNumber(json, "brg"),
                satellites = sat,
                accuracyMeters = acc,
                provider = prov,
                gpsTimestampMillis = gpsTs,
                captureTimestampMillis = capTs,
                make = text(json, "make") ?: "Unknown",
                model = text(json, "model") ?: "Unknown",
                imageHash = img,
                dotCodeHex = dots,
                fingerprint = fp
        )
    }

    private fun text(json: String, key: String): String? {
        val needle = "\"" + key + "\":\""
        val start = json.indexOf(needle)
        if (start < 0) {
            return null
        }
        var i = start + needle.length
        val out = StringBuilder()
        while (i < json.length) {
            val c = json[i]
            if (c == '\\' && i + 1 < json.length) {
                val next = json[i + 1]
                when (next) {
                    '"' -> out.append('"')
                    '\\' -> out.append('\\')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    else -> out.append(next)
                }
                i += 2
            } else if (c == '"') {
                return out.toString()
            } else {
                out.append(c)
                i++
            }
        }
        return null
    }

    private fun number(json: String, key: String): Double? {
        val raw = rawValue(json, key) ?: return null
        return raw.toDoubleOrNull()
    }

    private fun long(json: String, key: String): Long? {
        val raw = rawValue(json, key) ?: return null
        return raw.toLongOrNull() ?: raw.toDoubleOrNull()?.toLong()
    }

    private fun nullableNumber(json: String, key: String): Double? {
        val raw = rawValue(json, key) ?: return null
        if (raw == "null") {
            return null
        }
        return raw.toDoubleOrNull()
    }

    private fun rawValue(json: String, key: String): String? {
        val needle = "\"" + key + "\":"
        val start = json.indexOf(needle)
        if (start < 0) {
            return null
        }
        var i = start + needle.length
        while (i < json.length && json[i] == ' ') {
            i++
        }
        if (i >= json.length) {
            return null
        }
        if (json[i] == '"') {
            return text(json, key)
        }
        val begin = i
        while (i < json.length && json[i] != ',' && json[i] != '}') {
            i++
        }
        return json.substring(begin, i).trim()
    }

    // -- JPEG segment handling --

    private fun insertComSegment(jpeg: ByteArray, text: String): ByteArray {
        if (jpeg.size < 2 || jpeg[0] != 0xFF.toByte() || jpeg[1] != 0xD8.toByte()) {
            return jpeg
        }
        val payload = text.toByteArray(Charsets.UTF_8)
        val length = payload.size + 2
        if (length > 0xFFFF) {
            return jpeg
        }
        val out = ByteArray(jpeg.size + 4 + payload.size)
        out[0] = 0xFF.toByte()
        out[1] = 0xD8.toByte()
        out[2] = 0xFF.toByte()
        out[3] = 0xFE.toByte()
        out[4] = ((length ushr 8) and 0xFF).toByte()
        out[5] = (length and 0xFF).toByte()
        System.arraycopy(payload, 0, out, 6, payload.size)
        System.arraycopy(jpeg, 2, out, 6 + payload.size, jpeg.size - 2)
        return out
    }

    private fun findComText(jpeg: ByteArray): String? {
        var pos = 2
        while (pos + 3 < jpeg.size) {
            if (jpeg[pos] != 0xFF.toByte()) {
                return null
            }
            val marker = jpeg[pos + 1].toInt() and 0xFF
            if (marker == 0xD8 || marker == 0x01 || (marker in 0xD0..0xD7)) {
                pos += 2
                continue
            }
            if (marker == 0xD9) {
                return null
            }
            if (pos + 4 > jpeg.size) {
                return null
            }
            val length = ((jpeg[pos + 2].toInt() and 0xFF) shl 8) or
                    (jpeg[pos + 3].toInt() and 0xFF)
            if (length < 2 || pos + 2 + length > jpeg.size) {
                return null
            }
            if (marker == 0xFE) {
                val text = String(jpeg, pos + 4, length - 2, Charsets.UTF_8)
                if (text.startsWith(MARKER)) {
                    return text
                }
            }
            if (marker == 0xDA) {
                return null
            }
            pos += 2 + length
        }
        return null
    }

    /**
     * Rebuilds the JPEG without the fingerprint COM segment, so the image hash
     * can be recomputed over exactly the bytes that were hashed at capture.
     * Everything else, including any other COM segment and the scan data, is
     * copied byte for byte.
     */
    @JvmStatic
    fun jpegWithoutFingerprint(jpeg: ByteArray): ByteArray? {
        if (jpeg.size < 2 || jpeg[0] != 0xFF.toByte() || jpeg[1] != 0xD8.toByte()) {
            return null
        }
        val out = ArrayList<Byte>()
        out.add(0xFF.toByte())
        out.add(0xD8.toByte())
        var pos = 2
        while (pos + 1 < jpeg.size) {
            if (jpeg[pos] != 0xFF.toByte()) {
                return null
            }
            val marker = jpeg[pos + 1].toInt() and 0xFF
            if (marker == 0xD8 || marker == 0x01 || (marker in 0xD0..0xD7)) {
                out.add(jpeg[pos])
                out.add(jpeg[pos + 1])
                pos += 2
                continue
            }
            if (pos + 4 > jpeg.size) {
                return null
            }
            val length = ((jpeg[pos + 2].toInt() and 0xFF) shl 8) or
                    (jpeg[pos + 3].toInt() and 0xFF)
            if (length < 2 || pos + 2 + length > jpeg.size) {
                return null
            }
            if (marker == 0xDA) {
                for (i in pos until jpeg.size) {
                    out.add(jpeg[i])
                }
                return out.toByteArray()
            }
            val isFingerprint = marker == 0xFE &&
                    length - 2 >= MARKER.length &&
                    String(jpeg, pos + 4, MARKER.length, Charsets.UTF_8) == MARKER
            if (!isFingerprint) {
                for (i in pos until pos + 2 + length) {
                    out.add(jpeg[i])
                }
            }
            pos += 2 + length
        }
        return out.toByteArray()
    }
}
