package com.perfectgps.camera.media

import android.media.ExifInterface
import android.os.Build
import com.perfectgps.camera.model.GeoLocationSnapshot
import com.perfectgps.camera.util.ExifValueFormatter

object ExifMetadataWriter {
    @JvmStatic
    fun write(
            exif: ExifInterface,
            location: GeoLocationSnapshot?,
            captureTimeMillis: Long
    ) {
        val timestamp = ExifValueFormatter.exifDate(captureTimeMillis)
        exif.setAttribute(ExifInterface.TAG_DATETIME, timestamp)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, timestamp)
        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, timestamp)
        exif.setAttribute(ExifInterface.TAG_MAKE, safe(Build.MANUFACTURER))
        exif.setAttribute(ExifInterface.TAG_MODEL, safe(Build.MODEL))
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Perfect GPS Camera")
        // Pixels are already rotated upright before encoding, and the framework otherwise
        // persists an out-of-spec orientation of 0 when saveAttributes() runs.
        exif.setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL.toString()
        )
        if (location != null && location.isValid()) {
            exif.setAttribute(
                    ExifInterface.TAG_GPS_LATITUDE,
                    ExifValueFormatter.latitude(location.latitude())
            )
            exif.setAttribute(
                    ExifInterface.TAG_GPS_LATITUDE_REF,
                    ExifValueFormatter.latitudeReference(location.latitude())
            )
            exif.setAttribute(
                    ExifInterface.TAG_GPS_LONGITUDE,
                    ExifValueFormatter.longitude(location.longitude())
            )
            exif.setAttribute(
                    ExifInterface.TAG_GPS_LONGITUDE_REF,
                    ExifValueFormatter.longitudeReference(location.longitude())
            )
            exif.setAttribute(
                    ExifInterface.TAG_GPS_DATESTAMP,
                    ExifValueFormatter.gpsDate(location.timestampMillis())
            )
            exif.setAttribute(
                    ExifInterface.TAG_GPS_TIMESTAMP,
                    ExifValueFormatter.gpsTime(location.timestampMillis())
            )
            exif.setAttribute(
                    ExifInterface.TAG_GPS_SATELLITES,
                    Math.max(location.satellites(), 0).toString()
            )
            if (location.hasAltitude()) {
                exif.setAttribute(
                        ExifInterface.TAG_GPS_ALTITUDE,
                        ExifValueFormatter.altitude(location.altitudeMeters())
                )
                exif.setAttribute(
                        ExifInterface.TAG_GPS_ALTITUDE_REF,
                        ExifValueFormatter.altitudeReference(location.altitudeMeters())
                )
            }
            if (isFiniteNonNegative(location.speedMetersPerSecond())) {
                exif.setAttribute(
                        ExifInterface.TAG_GPS_SPEED,
                        ExifValueFormatter.speed(location.speedMetersPerSecond())
                )
                exif.setAttribute(ExifInterface.TAG_GPS_SPEED_REF, "K")
            }
            if (isFiniteDirection(location.bearingDegrees())) {
                exif.setAttribute(
                        ExifInterface.TAG_GPS_IMG_DIRECTION,
                        ExifValueFormatter.direction(location.bearingDegrees())
                )
                exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF, "T")
            }
        }
    }

    private fun isFiniteNonNegative(value: Double): Boolean = value.isFinite() && value >= 0.0

    private fun isFiniteDirection(value: Double): Boolean = value.isFinite() && value >= 0.0 && value < 360.0

    private fun safe(value: String?): String =
            value?.replace('\u0000', ' ')?.trim() ?: "Unknown"
}
