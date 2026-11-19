package com.perfectgps.camera.util

import com.perfectgps.camera.model.GeoLocationSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object PhotoNameFormatter {
    private const val MIN_DATE_MILLIS = 0L
    private const val MAX_DATE_MILLIS = 4_102_444_800_000L

    @JvmStatic
    fun format(date: Date?, cameraFacing: String?, location: GeoLocationSnapshot?): String {
        if (date == null) {
            throw IllegalArgumentException("date is required")
        }
        val facing = normalizeFacing(cameraFacing)
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        val timestamp = Math.max(MIN_DATE_MILLIS, Math.min(date.time, MAX_DATE_MILLIS))
        val name = StringBuilder("PGSC_")
                .append(formatter.format(Date(timestamp)))
                .append('_')
        if (location != null && location.isValid()) {
            name.append(
                    String.format(
                            Locale.US,
                            "%.4f%c",
                            Math.abs(location.latitude()),
                            if (location.latitude() >= 0.0) 'N' else 'S'
                    )
            )
            name.append(
                    String.format(
                            Locale.US,
                            "_%08.4f%c",
                            Math.abs(location.longitude()),
                            if (location.longitude() >= 0.0) 'E' else 'W'
                    )
            )
        } else {
            name.append(facing)
        }
        return name.append(".jpg").toString()
    }

    private fun normalizeFacing(value: String?): String {
        if (value == "back") {
            return "BACK"
        }
        if (value == "front") {
            return "FRONT"
        }
        throw IllegalArgumentException("cameraFacing must be back or front")
    }
}
