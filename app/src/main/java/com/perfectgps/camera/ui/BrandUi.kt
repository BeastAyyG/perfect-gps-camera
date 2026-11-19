package com.perfectgps.camera.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.perfectgps.camera.R

object BrandUi {
    @JvmStatic
    fun dp(context: Context?, value: Float): Int = Math.round(
            value * (context?.resources?.displayMetrics?.density
                    ?: android.content.res.Resources.getSystem().displayMetrics.density)
    )

    @JvmStatic
    fun text(
            context: Context,
            value: String,
            sizeSp: Float,
            color: Int,
            medium: Boolean
    ): TextView {
        val view = TextView(context)
        view.text = value
        view.textSize = sizeSp
        view.setTextColor(color)
        view.typeface = Typeface.create(
                if (medium) "sans-serif-medium" else "sans-serif",
                Typeface.NORMAL
        )
        return view
    }

    @JvmStatic
    fun rounded(color: Int, radius: Float): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.setColor(color)
        drawable.cornerRadius = radius
        return drawable
    }

    @JvmStatic
    fun outlined(color: Int, strokeColor: Int, radius: Float, strokeWidth: Int): GradientDrawable {
        val drawable = rounded(color, radius)
        drawable.setStroke(strokeWidth, strokeColor)
        return drawable
    }

    @JvmStatic
    fun pill(color: Int): GradientDrawable = rounded(color, Float.MAX_VALUE)

    @JvmStatic
    fun linearParams(width: Int, height: Int): LinearLayout.LayoutParams =
            LinearLayout.LayoutParams(width, height)

    @JvmStatic
    fun linearParams(width: Int, height: Int, weight: Float): LinearLayout.LayoutParams =
            LinearLayout.LayoutParams(width, height, weight)

    @JvmStatic
    fun addStartMargin(params: LinearLayout.LayoutParams, margin: Int) {
        params.marginStart = margin
    }

    @JvmStatic
    fun addEndMargin(params: LinearLayout.LayoutParams, margin: Int) {
        params.marginEnd = margin
    }

    @JvmStatic
    fun addBottomMargin(params: LinearLayout.LayoutParams, margin: Int) {
        params.bottomMargin = margin
    }

    @JvmStatic
    fun addTopMargin(params: LinearLayout.LayoutParams, margin: Int) {
        params.topMargin = margin
    }

    @JvmStatic
    fun configureWindow(activity: Activity) {
        val window = activity.window
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = activity.getColor(R.color.camera_navy_deep)
        hideSystemUi(activity)
    }

    @Suppress("DEPRECATION")
    @JvmStatic
    fun hideSystemUi(activity: Activity) {
        activity.window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
    }

    @JvmStatic
    fun center(view: View) {
        if (view is TextView) {
            view.gravity = Gravity.CENTER
        }
    }
}
