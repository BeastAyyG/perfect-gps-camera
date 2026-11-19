package com.perfectgps.camera.util

object WatermarkPolicy {
    const val MIN_LONG_EDGE = 1000
    const val MAX_LONG_EDGE = 8000
    const val HEADLINE_RATIO = 0.026f
    const val META_RATIO = 0.0185f
    const val MARGIN_RATIO = 0.028f
    const val PANEL_RATIO = 0.30f
    const val LINE_SPACING = 1.22f
    const val TEXT_EDGE = 0.08f

    @JvmStatic
    fun uprightRotation(degrees: Int): Int {
        var wrapped = degrees % 360
        if (wrapped < 0) {
            wrapped += 360
        }
        val rounded = (wrapped + 45) / 90 * 90
        return if (rounded == 360) 0 else rounded
    }

    @JvmStatic
    fun buildLines(
            placeName: String?,
            coordinates: String?,
            noFixLabel: String?
    ): Array<String> {
        val headline = trimmed(placeName)
        val coords = trimmed(coordinates)
        val missing = trimmed(noFixLabel)

        val lines = ArrayList<String>()
        lines.add(if (headline.isNotEmpty()) headline else missing)
        lines.add(if (coords.isNotEmpty()) coords else missing)
        return lines.toTypedArray()
    }

    @JvmStatic
    fun headlineSize(longEdge: Int): Int =
            Math.max(1, Math.round(bounded(longEdge) * HEADLINE_RATIO))

    @JvmStatic
    fun metaSize(longEdge: Int): Int =
            Math.max(1, Math.round(bounded(longEdge) * META_RATIO))

    @JvmStatic
    fun margin(longEdge: Int): Int =
            Math.max(1, Math.round(bounded(longEdge) * MARGIN_RATIO))

    @JvmStatic
    fun panelHeight(longEdge: Int): Int =
            Math.max(1, Math.round(bounded(longEdge) * PANEL_RATIO))

    @JvmStatic
    fun bounded(longEdge: Int): Float =
            Math.max(MIN_LONG_EDGE.toFloat(), Math.min(MAX_LONG_EDGE.toFloat(), longEdge.toFloat()))

    @JvmStatic
    fun trimmed(value: String?): String = value?.trim() ?: ""
}
