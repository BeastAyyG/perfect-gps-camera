package com.perfectgps.camera.util

import com.perfectgps.camera.model.GeoLocationSnapshot
import java.util.concurrent.TimeUnit

object LocationFreshnessPolicy {
    private const val MAX_FUTURE_SKEW_MILLIS = 5_000L

    @JvmStatic
    fun isFresh(
            location: GeoLocationSnapshot?,
            nowMillis: Long,
            maxAgeMillis: Long
    ): Boolean {
        if (location == null || !location.isValid() || maxAgeMillis <= 0L) {
            return false
        }
        val age = nowMillis - location.timestampMillis()
        if (age < -MAX_FUTURE_SKEW_MILLIS) {
            return false
        }
        return age <= TimeUnit.MILLISECONDS.toMillis(maxAgeMillis)
    }
}
