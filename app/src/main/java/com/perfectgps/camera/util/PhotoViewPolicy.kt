package com.perfectgps.camera.util

object PhotoViewPolicy {
    const val MIN_SCALE = 1.0f
    const val MAX_SCALE = 4.0f
    private const val EPSILON = 0.001f

    @JvmStatic
    fun fitScale(viewWidth: Int, viewHeight: Int, drawableWidth: Int, drawableHeight: Int): Float {
        if (viewWidth <= 0 || viewHeight <= 0 || drawableWidth <= 0 || drawableHeight <= 0) {
            return 1.0f
        }
        return Math.min(viewWidth.toFloat() / drawableWidth, viewHeight.toFloat() / drawableHeight)
    }

    @JvmStatic
    fun clampScale(scale: Float): Float {
        if (scale.isNaN()) {
            return MIN_SCALE
        }
        return Math.max(MIN_SCALE, Math.min(scale, MAX_SCALE))
    }

    @JvmStatic
    fun isZoomed(scale: Float): Boolean = scale > MIN_SCALE + EPSILON

    @JvmStatic
    fun shownSize(drawableSize: Int, fit: Float): Float {
        if (drawableSize <= 0) {
            return 0.0f
        }
        return drawableSize * fit
    }

    @JvmStatic
    fun clampOffset(offset: Float, viewSize: Int, shownSize: Float, scale: Float): Float {
        if (viewSize <= 0 || shownSize <= 0.0f) {
            return 0.0f
        }
        val limit = Math.max(0.0f, (shownSize * scale - viewSize) / 2.0f)
        return Math.max(-limit, Math.min(offset, limit))
    }

    @JvmStatic
    fun focusOffset(
            focus: Float,
            currentOffset: Float,
            currentScale: Float,
            nextScale: Float
    ): Float {
        if (currentScale <= 0.0f || nextScale <= 0.0f || currentOffset.isNaN()) {
            return 0.0f
        }
        val ratio = nextScale / currentScale
        return focus - (focus - currentOffset) * ratio
    }
}
