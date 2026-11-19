package com.perfectgps.camera.util

object CameraOrientationCalculator {
    @JvmStatic
    fun jpegOrientation(
            sensorOrientation: Int,
            deviceOrientation: Int,
            frontFacing: Boolean
    ): Int {
        val sensor = normalize(sensorOrientation)
        val device = normalize(deviceOrientation)
        if (frontFacing) {
            var result = normalize(sensor + device)
            result = normalize(360 - result)
            return result
        }
        return normalize(sensor - device)
    }

    @JvmStatic
    fun relativeRotation(
            sensorOrientation: Int,
            displayRotation: Int,
            frontFacing: Boolean
    ): Int {
        val sensor = normalize(sensorOrientation)
        val display = normalize(displayRotation)
        val sign = if (frontFacing) 1 else -1
        return normalize(sensor - display * sign)
    }

    private fun normalize(value: Int): Int {
        val normalized = value % 360
        return if (normalized < 0) normalized + 360 else normalized
    }
}
