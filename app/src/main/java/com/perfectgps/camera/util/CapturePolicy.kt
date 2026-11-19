package com.perfectgps.camera.util

import com.perfectgps.camera.model.GeoLocationSnapshot

object CapturePolicy {
    @JvmStatic
    fun canCapture(
            cameraReady: Boolean,
            locationRequired: Boolean,
            location: GeoLocationSnapshot?
    ): Boolean {
        if (!cameraReady) {
            return false
        }
        return !locationRequired || location != null && location.isValid()
    }
}
