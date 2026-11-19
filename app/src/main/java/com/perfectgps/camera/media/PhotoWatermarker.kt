package com.perfectgps.camera.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import com.perfectgps.camera.util.WatermarkPolicy
import java.io.ByteArrayOutputStream

object PhotoWatermarker {
    const val JPEG_QUALITY = 95
    private const val PANEL_COLOR = 0xCC000000.toInt()
    private const val HEADLINE_COLOR = 0xFFFFFFFF.toInt()
    private const val META_COLOR = 0xFFE6E9EC.toInt()

    @JvmStatic
    fun apply(
            jpeg: ByteArray?,
            lines: Array<out String?>?,
            rotationDegrees: Int,
            mirror: Boolean
    ): ByteArray? {
        if (jpeg == null || jpeg.isEmpty() || lines == null || lines.isEmpty()) {
            return jpeg
        }
        val decoded: Bitmap = try {
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        } catch (error: OutOfMemoryError) {
            return jpeg
        } ?: return jpeg
        val upright = rotateToUpright(decoded, rotationDegrees, mirror)
        try {
            val output = Bitmap.createBitmap(upright.width, upright.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            canvas.drawBitmap(upright, 0.0f, 0.0f, null)
            drawWatermark(canvas, output.width, output.height, lines)
            val buffer = ByteArrayOutputStream(jpeg.size)
            if (!output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, buffer)) {
                return jpeg
            }
            val stamped = buffer.toByteArray()
            return if (stamped.isNotEmpty()) stamped else jpeg
        } catch (error: OutOfMemoryError) {
            return jpeg
        } finally {
            if (upright !== decoded) {
                upright.recycle()
            }
            decoded.recycle()
        }
    }

    @JvmStatic
    fun rotateToUpright(source: Bitmap, rotationDegrees: Int, mirror: Boolean): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(WatermarkPolicy.uprightRotation(rotationDegrees).toFloat())
        if (mirror) {
            matrix.postScale(-1.0f, 1.0f)
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun drawWatermark(
            canvas: Canvas,
            width: Int,
            height: Int,
            lines: Array<out String?>
    ) {
        val longEdge = Math.max(width, height)
        val margin = WatermarkPolicy.margin(longEdge)
        val headlineSize = WatermarkPolicy.headlineSize(longEdge)
        val metaSize = WatermarkPolicy.metaSize(longEdge)
        val panelHeight = WatermarkPolicy.panelHeight(longEdge)
        val lineStep = Math.round(headlineSize * WatermarkPolicy.LINE_SPACING)

        val panel = Paint(Paint.ANTI_ALIAS_FLAG)
        panel.color = PANEL_COLOR
        canvas.drawRect(
                0.0f,
                (height - panelHeight).toFloat(),
                width.toFloat(),
                height.toFloat(),
                panel
        )

        val headline = textPaint(
                headlineSize,
                HEADLINE_COLOR,
                Typeface.create("sans-serif-medium", Typeface.NORMAL)
        )
        val meta = textPaint(metaSize, META_COLOR, Typeface.create("sans-serif", Typeface.NORMAL))

        var baseline = (height - margin).toFloat()
        for (index in lines.size - 1 downTo 0) {
            val line = lines[index]
            if (line == null || line.trim().isEmpty()) {
                continue
            }
            val paint: Paint
            val size: Int
            if (index == 0) {
                paint = headline
                size = headlineSize
            } else {
                paint = meta
                size = metaSize
            }
            baseline -= size * 0.18f
            canvas.drawText(line, margin.toFloat(), baseline, paint)
            baseline -= lineStep
        }
    }

    private fun textPaint(textSize: Int, color: Int, typeface: Typeface): Paint {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        paint.color = color
        paint.textSize = textSize.toFloat()
        paint.typeface = typeface
        paint.letterSpacing = WatermarkPolicy.TEXT_EDGE
        return paint
    }
}
