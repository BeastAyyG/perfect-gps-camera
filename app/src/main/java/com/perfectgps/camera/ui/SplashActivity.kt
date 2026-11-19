package com.perfectgps.camera.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.perfectgps.camera.R

class SplashActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var navigating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BrandUi.configureWindow(this)
        setContentView(createContentView())
        animateBrand()
        handler.postDelayed({ openNext() }, EXIT_DELAY_MILLIS)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun createContentView(): View {
        val root = FrameLayout(this)
        root.setBackgroundResource(R.drawable.bg_splash_gradient)

        val glow = View(this)
        glow.background = BrandUi.rounded(0x263B82F6, BrandUi.dp(this, 180.0f).toFloat())
        val glowParams = FrameLayout.LayoutParams(
                BrandUi.dp(this, 300.0f),
                BrandUi.dp(this, 300.0f),
                Gravity.TOP or Gravity.END
        )
        glowParams.setMargins(0, BrandUi.dp(this, -80.0f), BrandUi.dp(this, -74.0f), 0)
        root.addView(glow, glowParams)

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.gravity = Gravity.CENTER
        content.setPadding(
                BrandUi.dp(this, 28.0f),
                BrandUi.dp(this, 24.0f),
                BrandUi.dp(this, 28.0f),
                BrandUi.dp(this, 24.0f)
        )

        val markFrame = FrameLayout(this)
        markFrame.background = BrandUi.outlined(
                0x33FFFFFF,
                0x55F2A900,
                BrandUi.dp(this, 86.0f).toFloat(),
                BrandUi.dp(this, 1.0f)
        )
        val mark = ImageView(this)
        mark.setImageResource(R.drawable.ic_srm_mark)
        mark.contentDescription = getString(R.string.brand_mark_description)
        markFrame.addView(mark, FrameLayout.LayoutParams(
                BrandUi.dp(this, 64.0f),
                BrandUi.dp(this, 64.0f),
                Gravity.CENTER
        ))
        content.addView(markFrame, BrandUi.linearParams(
                BrandUi.dp(this, 172.0f),
                BrandUi.dp(this, 172.0f)
        ))

        val eyebrow = BrandUi.text(
                this,
                getString(R.string.splash_university),
                11.0f,
                getColor(R.color.camera_gold_bright),
                true
        )
        eyebrow.letterSpacing = 0.18f
        BrandUi.center(eyebrow)
        val eyebrowParams = BrandUi.linearParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        )
        BrandUi.addTopMargin(eyebrowParams, BrandUi.dp(this, 24.0f))
        content.addView(eyebrow, eyebrowParams)

        val title = BrandUi.text(
                this,
                getString(R.string.splash_title),
                27.0f,
                getColor(R.color.camera_white),
                true
        )
        title.letterSpacing = 0.08f
        BrandUi.center(title)
        val titleParams = BrandUi.linearParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        )
        BrandUi.addTopMargin(titleParams, BrandUi.dp(this, 8.0f))
        content.addView(title, titleParams)

        val subtitle = BrandUi.text(
                this,
                getString(R.string.splash_product),
                10.0f,
                getColor(R.color.camera_text_secondary),
                false
        )
        subtitle.letterSpacing = 0.14f
        BrandUi.center(subtitle)
        val subtitleParams = BrandUi.linearParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        )
        BrandUi.addTopMargin(subtitleParams, BrandUi.dp(this, 10.0f))
        content.addView(subtitle, subtitleParams)

        val footer = BrandUi.text(
                this,
                getString(R.string.privacy_note),
                9.5f,
                0x8FFFFFFF.toInt(),
                false
        )
        footer.letterSpacing = 0.12f
        footer.gravity = Gravity.CENTER
        footer.setLineSpacing(0.0f, 1.15f)
        val footerParams = BrandUi.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        )
        BrandUi.addTopMargin(footerParams, BrandUi.dp(this, 64.0f))
        content.addView(footer, footerParams)

        root.addView(content, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
        ))
        return root
    }

    private fun animateBrand() {
        val root = findViewById<View>(android.R.id.content)
        root.translationY = BrandUi.dp(this, 14.0f).toFloat()
        root.animate()
                .translationY(0.0f)
                .setDuration(520L)
                .setInterpolator(DecelerateInterpolator())
                .start()
    }

    private fun openNext() {
        if (navigating || isFinishing) {
            return
        }
        navigating = true
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val destination = if (preferences.getBoolean(ONBOARDING_COMPLETE, false)) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, OnboardingActivity::class.java)
        }
        startActivity(destination)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    companion object {
        private const val PREFS_NAME = "srm_ap_event_camera"
        private const val ONBOARDING_COMPLETE = "onboarding_complete"
        private const val EXIT_DELAY_MILLIS = 1_350L
    }
}
