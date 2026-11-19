package com.perfectgps.camera.util

import com.perfectgps.camera.model.GeoLocationSnapshot
import java.util.Locale

object GeoTextFormatter {
    @JvmStatic
    fun formatCoordinates(location: GeoLocationSnapshot?, locale: Locale?): String {
        if (location == null || !location.isValid()) {
            return ""
        }
        return String.format(locale, "%.6f, %.6f", location.latitude(), location.longitude())
    }

    @JvmStatic
    fun formatDms(location: GeoLocationSnapshot?, locale: Locale?): String {
        if (location == null || !location.isValid()) {
            return ""
        }
        return formatComponent(location.latitude(), "NS", locale) +
                " " +
                formatComponent(location.longitude(), "EW", locale)
    }

    @JvmStatic
    fun formatAccuracy(location: GeoLocationSnapshot?, locale: Locale?): String {
        if (location == null || !location.isValid()) {
            return ""
        }
        return String.format(locale, "±%.1f m", location.accuracyMeters())
    }

    private fun formatComponent(coordinate: Double, hemispheres: String, locale: Locale?): String {
        val hemisphere = if (coordinate >= 0.0) hemispheres[0] else hemispheres[1]
        val absolute = Math.abs(coordinate)
        val degrees = absolute.toInt()
        val minutesValue = (absolute - degrees) * 60.0
        val minutes = minutesValue.toInt()
        val seconds = (minutesValue - minutes) * 60.0
        return String.format(
                locale,
                "%d°%02d'%04.1f\"%c",
                degrees,
                minutes,
                seconds,
                hemisphere
        )
    }
}
