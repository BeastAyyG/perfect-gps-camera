package com.perfectgps.camera.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.perfectgps.camera.util.WatermarkPolicy

/**
 * The printer-dot half of the fingerprint: a grid of tiny dots drawn into the
 * photo, in the style of the invisible tracking dots colour laser printers
 * put on every page.
 *
 * The dots live in the free strip at the top of the dark watermark panel,
 * above the text, on a uniform dark background so they are easy to find and
 * read back. Each dot is one bit of the 8-byte code from
 * [PhotoFingerprint.dotCodeHex] (64 bits), framed by a 3-dot start marker and
 * a 3-dot end marker. A dot is drawn for a 1 bit and skipped for a 0 bit.
 *
 * The dots are small and a muted grey, so they read as part of the panel to
 * the eye and do not change what the photo shows. They are read back by
 * [decode], which samples the same fixed grid positions and thresholds the
 * brightness. The code in the dots must match the code stored in the
 * fingerprint data block ([PhotoFingerprint]), so the two marks cross-check.
 *
 * Geometry is derived only from the image size through [WatermarkPolicy], so
 * the encoder and the decoder always agree on where the dots are.
 */
object DotPattern {
    const val BITS = 64
    const val MARKER_BITS = 3
    const val TOTAL_POSITIONS = MARKER_BITS + BITS + MARKER_BITS

    private const val DOT_COLOR = 0xFF8A939E.toInt()
    private const val LUMINANCE_THRESHOLD = 100

    /** Pixel positions and sizes for a given image, shared by draw and decode. */
    class Geometry(
            val rowY: Int,
            val startX: Int,
            val dotSize: Int,
            val pitch: Int
    ) {
        fun centerX(position: Int): Int = startX + position * pitch + dotSize / 2
        fun centerY(): Int = rowY + dotSize / 2
    }

    @JvmStatic
    fun geometry(width: Int, height: Int): Geometry {
        val longEdge = maxOf(width, height)
        val panelHeight = WatermarkPolicy.panelHeight(longEdge)
        val margin = WatermarkPolicy.margin(longEdge)
        val dotSize = maxOf(3, Math.round(WatermarkPolicy.bounded(longEdge) * 0.002f))
        val pitch = dotSize * 3
        val totalWidth = (TOTAL_POSITIONS - 1) * pitch + dotSize
        val startX = maxOf(0, (width - totalWidth) / 2)
        val rowY = height - panelHeight + margin
        return Geometry(rowY, startX, dotSize, pitch)
    }

    /** 64 bits for a 16-hex-character code, most significant bit first. */
    @JvmStatic
    fun bitsFor(codeHex: String): BooleanArray {
        val bits = BooleanArray(BITS)
        val clean = codeHex.lowercase()
        for (i in 0 until BITS) {
            val byteIndex = i / 8
            val bitIndex = 7 - (i % 8)
            if (byteIndex * 2 + 1 >= clean.length) {
                break
            }
            val hex = clean.substring(byteIndex * 2, byteIndex * 2 + 2)
            val value = hex.toIntOrNull(16) ?: 0
            bits[i] = ((value ushr bitIndex) and 1) == 1
        }
        return bits
    }

    /** 16-hex-character code for 64 bits, most significant bit first. */
    @JvmStatic
    fun hexFor(bits: BooleanArray): String {
        val out = StringBuilder()
        for (byteIndex in 0 until BITS / 8) {
            var value = 0
            for (bitIndex in 0 until 8) {
                if (bits[byteIndex * 8 + bitIndex]) {
                    value = value or (1 shl (7 - bitIndex))
                }
            }
            val hex = Integer.toHexString(value)
            if (hex.length < 2) {
                out.append('0')
            }
            out.append(hex)
        }
        return out.toString()
    }

    /** Draws the dot grid onto a canvas, in the panel's free strip. */
    @JvmStatic
    fun draw(canvas: Canvas, width: Int, height: Int, codeHex: String) {
        val geo = geometry(width, height)
        val bits = bitsFor(codeHex)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = DOT_COLOR
        paint.style = Paint.Style.FILL
        for (position in 0 until TOTAL_POSITIONS) {
            val on = when {
                position < MARKER_BITS -> true
                position >= MARKER_BITS + BITS -> true
                else -> bits[position - MARKER_BITS]
            }
            if (!on) {
                continue
            }
            val left = (geo.startX + position * geo.pitch).toFloat()
            val top = geo.rowY.toFloat()
            canvas.drawRect(
                    left,
                    top,
                    left + geo.dotSize,
                    top + geo.dotSize,
                    paint
            )
        }
    }

    /**
     * Reads the dot grid back from a bitmap. Returns the 16-hex-character
     * code, or null when the start/end markers do not read, which means the
     * dots are not there or the image was altered or cropped.
     */
    @JvmStatic
    fun decode(bitmap: Bitmap?): String? {
        if (bitmap == null) {
            return null
        }
        return decode(bitmap.width, bitmap.height) { x, y -> bitmap.getPixel(x, y) }
    }

    @JvmStatic
    fun decode(
            width: Int,
            height: Int,
            pixel: (x: Int, y: Int) -> Int
    ): String? {
        val geo = geometry(width, height)
        val y = geo.centerY()
        if (y < 0 || y >= height) {
            return null
        }
        val bits = BooleanArray(TOTAL_POSITIONS)
        for (position in 0 until TOTAL_POSITIONS) {
            val x = geo.centerX(position)
            if (x < 0 || x >= width) {
                return null
            }
            bits[position] = isLit(pixel(x, y))
        }
        for (i in 0 until MARKER_BITS) {
            if (!bits[i] || !bits[MARKER_BITS + BITS + i]) {
                return null
            }
        }
        val code = BooleanArray(BITS)
        for (i in 0 until BITS) {
            code[i] = bits[MARKER_BITS + i]
        }
        return hexFor(code)
    }

    private fun isLit(argb: Int): Boolean {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return (r + g + b) / 3 >= LUMINANCE_THRESHOLD
    }
}
