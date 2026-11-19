package com.perfectgps.camera.util

object PreviewGesturePolicy {

    const val DIRECTION_NONE = 0
    const val DIRECTION_UP = -1
    const val DIRECTION_DOWN = 1

    @JvmStatic
    fun verticalSwipeDirection(dx: Float, dy: Float, slop: Float): Int {
        if (slop <= 0.0f) {
            return DIRECTION_NONE
        }
        val absY = Math.abs(dy)
        if (absY < slop || absY <= Math.abs(dx)) {
            return DIRECTION_NONE
        }
        return if (dy < 0.0f) DIRECTION_UP else DIRECTION_DOWN
    }

    @JvmStatic
    fun isDoubleTap(
        elapsedMillis: Long,
        timeoutMillis: Int,
        dx: Float,
        dy: Float,
        slop: Float
    ): Boolean {
        if (elapsedMillis < 0L || timeoutMillis <= 0) {
            return false
        }
        return elapsedMillis <= timeoutMillis &&
                Math.abs(dx) <= slop &&
                Math.abs(dy) <= slop
    }
}
