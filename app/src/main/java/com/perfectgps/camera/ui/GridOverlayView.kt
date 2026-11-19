package com.perfectgps.camera.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class GridOverlayView(context: Context) : View(context) {
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    var isGridEnabled: Boolean = false
        set(enabled) {
            if (field != enabled) {
                field = enabled
                invalidate()
            }
        }

    init {
        shadePaint.color = Color.argb(120, 0, 0, 0)
        shadePaint.strokeWidth = dp(2.0f)
        linePaint.color = Color.argb(170, 255, 255, 255)
        linePaint.strokeWidth = dp(1.0f)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isGridEnabled) {
            return
        }
        val thirdWidth = width / 3.0f
        val thirdHeight = height / 3.0f
        for (pass in 0..1) {
            val paint = if (pass == 0) shadePaint else linePaint
            canvas.drawLine(thirdWidth, 0.0f, thirdWidth, height.toFloat(), paint)
            canvas.drawLine(thirdWidth * 2.0f, 0.0f, thirdWidth * 2.0f, height.toFloat(), paint)
            canvas.drawLine(0.0f, thirdHeight, width.toFloat(), thirdHeight, paint)
            canvas.drawLine(0.0f, thirdHeight * 2.0f, width.toFloat(), thirdHeight * 2.0f, paint)
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
