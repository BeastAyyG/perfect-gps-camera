package com.perfectgps.camera.util

object ZoomPolicy {

    @JvmStatic
    fun clamp(requestedRatio: Float, maxDigitalZoom: Float): Float {
        val maximum = if (maxDigitalZoom <= 1.0f) 1.0f else maxDigitalZoom
        return Math.max(1.0f, Math.min(requestedRatio, maximum))
    }

    @JvmStatic
    fun afterLensSwitch(): Float = 1.0f
}
