package com.perfectgps.camera.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ExifValueFormatter {
    private const val COORDINATE_SCALE = 10_000
    private const val RATIONAL_SCALE = 1_000_000

    @JvmStatic
    fun latitude(value: Double): String = coordinate(value)

    @JvmStatic
    fun longitude(value: Double): String = coordinate(value)

    @JvmStatic
    fun latitudeReference(value: Double): String = if (value >= 0.0) "N" else "S"

    @JvmStatic
    fun longitudeReference(value: Double): String = if (value >= 0.0) "E" else "W"

    @JvmStatic
    fun altitude(value: Double): String = rational(Math.abs(value))

    @JvmStatic
    fun altitudeReference(value: Double): String = if (value < 0.0) "1" else "0"

    @JvmStatic
    fun speed(value: Double): String = rational(Math.abs(value))

    @JvmStatic
    fun direction(value: Double): String = rational(Math.abs(value))

    @JvmStatic
    fun gpsDate(timestampMillis: Long): String = format("yyyy:MM:dd", timestampMillis)

    @JvmStatic
    fun gpsTime(timestampMillis: Long): String = format("HH:mm:ss", timestampMillis)

    @JvmStatic
    fun exifDate(timestampMillis: Long): String = format("yyyy:MM:dd HH:mm:ss", timestampMillis)

    private fun coordinate(value: Double): String {
        val absolute = Math.abs(value)
        val degrees = Math.floor(absolute).toInt()
        val minutesValue = (absolute - degrees) * 60.0
        val minutes = Math.floor(minutesValue).toInt()
        val seconds = (minutesValue - minutes) * 60.0
        val scaledSeconds = BigDecimal.valueOf(seconds)
                .multiply(BigDecimal.valueOf(COORDINATE_SCALE.toLong()))
                .setScale(0, RoundingMode.HALF_UP)
                .unscaledValue().toLong()
        return "$degrees/1,$minutes/1,$scaledSeconds/$COORDINATE_SCALE"
    }

    private fun rational(value: Double): String {
        if (!value.isFinite()) {
            return "0/1"
        }
        val scaled = BigDecimal.valueOf(value)
                .setScale(6, RoundingMode.HALF_UP)
                .unscaledValue().toLong()
        val divisor = RATIONAL_SCALE.toLong()
        var a = Math.abs(scaled)
        var b = divisor
        while (b != 0L) {
            val next = a % b
            a = b
            b = next
        }
        val divisorReduced = divisor / Math.max(a, 1L)
        val numeratorReduced = Math.abs(scaled) / Math.max(a, 1L)
        return "$numeratorReduced/$divisorReduced"
    }

    private fun format(pattern: String, timestampMillis: Long): String {
        val formatter = SimpleDateFormat(pattern, Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date(timestampMillis))
    }
}
