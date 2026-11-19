package com.perfectgps.camera.util

object BitmapDecodePolicy {
    private const val MAX_SAMPLE_SIZE = 1024

    @JvmStatic
    fun previewTarget(displayWidthPixels: Int): Int = Math.max(1, displayWidthPixels)

    @JvmStatic
    fun sampleSize(sourceLongEdge: Int, targetLongEdge: Int): Int {
        if (sourceLongEdge <= 0 || targetLongEdge <= 0) {
            return 1
        }
        val limit = targetLongEdge * 2
        var sample = 1
        while (sourceLongEdge / sample > limit && sample < MAX_SAMPLE_SIZE) {
            sample *= 2
        }
        return sample
    }
}
