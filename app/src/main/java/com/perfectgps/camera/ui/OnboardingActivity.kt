package com.perfectgps.camera.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.ViewFlipper
import com.perfectgps.camera.R
import java.util.Locale

class OnboardingActivity : Activity() {
    private class Page(
            val illustration: Int,
            val eyebrow: String,
            val title: String,
            val body: String,
            val featureIcon: Int,
            val featureText: String
    )

    private lateinit var pages: Array<Page>
    private lateinit var flipper: ViewFlipper
    private lateinit var indicator: LinearLayout
    private lateinit var nextButton: TextView
    private var currentPage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pages = arrayOf(
                Page(
                        R.drawable.illustration_capture,
                        getString(R.string.onboarding_capture_eyebrow),
                        getString(R.string.onboarding_capture_title),
                        getString(R.string.onboarding_capture_body),
                        R.drawable.ic_location_pin,
                        getString(R.string.onboarding_capture_feature)
                ),
                Page(
                        R.drawable.illustration_organize,
                        getString(R.string.onboarding_organize_eyebrow),
                        getString(R.string.onboarding_organize_title),
                        getString(R.string.onboarding_organize_body),
                        R.drawable.ic_gallery,
                        getString(R.string.onboarding_organize_feature)
                ),
                Page(
                        R.drawable.illustration_report,
                        getString(R.string.onboarding_report_eyebrow),
                        getString(R.string.onboarding_report_title),
                        getString(R.string.onboarding_report_body),
                        R.drawable.ic_report,
                        getString(R.string.onboarding_report_feature)
                )
        )
        BrandUi.configureWindow(this)
        setContentView(createContentView())
        showPage(0, false)
    }

    @Deprecated("Deprecated in the activity API")
    override fun onBackPressed() {
        if (currentPage > 0) {
            showPage(currentPage - 1, true)
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    private fun createContentView(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(getColor(R.color.camera_navy_deep))
        root.setPadding(
                BrandUi.dp(this, 20.0f),
                BrandUi.dp(this, 18.0f),
                BrandUi.dp(this, 20.0f),
                BrandUi.dp(this, 20.0f)
        )

        root.addView(createTopBar(), BrandUi.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        flipper = ViewFlipper(this)
        flipper.inAnimation = getAnimation(android.R.anim.fade_in)
        flipper.outAnimation = getAnimation(android.R.anim.fade_out)
        val flipperParams = BrandUi.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        )
        BrandUi.addTopMargin(flipperParams, BrandUi.dp(this, 12.0f))
        root.addView(flipper, flipperParams)
        for (page in pages) {
            flipper.addView(createPage(page), FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }

        root.addView(createBottomBar(), BrandUi.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        return root
    }

    private fun createTopBar(): View {
        val bar = LinearLayout(this)
        bar.gravity = Gravity.CENTER_VERTICAL

        val mark = ImageView(this)
        mark.setImageResource(R.drawable.ic_srm_mark)
        mark.contentDescription = getString(R.string.brand_mark_description)
        bar.addView(mark, BrandUi.linearParams(BrandUi.dp(this, 44.0f), BrandUi.dp(this, 44.0f)))

        val brand = LinearLayout(this)
        brand.orientation = LinearLayout.VERTICAL
        brand.gravity = Gravity.CENTER_VERTICAL
        val title = BrandUi.text(this, getString(R.string.splash_university), 11.0f, getColor(R.color.camera_white), true)
        title.letterSpacing = 0.1f
        val subtitle = BrandUi.text(this, getString(R.string.event_documentation), 9.0f, getColor(R.color.camera_gold_bright), true)
        subtitle.letterSpacing = 0.12f
        brand.addView(title, BrandUi.linearParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        brand.addView(subtitle, BrandUi.linearParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        val brandParams = BrandUi.linearParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
        BrandUi.addStartMargin(brandParams, BrandUi.dp(this, 12.0f))
        bar.addView(brand, brandParams)

        val skip = BrandUi.text(this, getString(R.string.onboarding_skip).uppercase(Locale.ROOT), 10.0f, getColor(R.color.camera_text_secondary), true)
        skip.letterSpacing = 0.1f
        skip.gravity = Gravity.CENTER
        skip.minHeight = BrandUi.dp(this, 40.0f)
        skip.setPadding(BrandUi.dp(this, 14.0f), 0, BrandUi.dp(this, 14.0f), 0)
        skip.background = BrandUi.outlined(
                0x00000000,
                getColor(R.color.camera_line),
                BrandUi.dp(this, 20.0f).toFloat(),
                BrandUi.dp(this, 1.0f)
        )
        skip.setOnClickListener { finishOnboarding() }
        bar.addView(skip, BrandUi.linearParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                BrandUi.dp(this, 40.0f)
        ))
        return bar
    }

    private fun createPage(page: Page): View {
        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.setClipToPadding(false)

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.gravity = Gravity.CENTER_HORIZONTAL
        content.setPadding(0, BrandUi.dp(this, 10.0f), 0, BrandUi.dp(this, 20.0f))

        val eyebrow = BrandUi.text(this, page.eyebrow, 10.0f, getColor(R.color.camera_gold_bright), true)
        eyebrow.letterSpacing = 0.14f
        content.addView(eyebrow, BrandUi.linearParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val illustration = ImageView(this)
        illustration.setImageResource(page.illustration)
        illustration.contentDescription = getString(R.string.onboarding_illustration_description)
        illustration.adjustViewBounds = true
        illustration.scaleType = ImageView.ScaleType.FIT_CENTER
        val stage = LinearLayout(this)
        stage.gravity = Gravity.CENTER
        stage.minimumHeight = BrandUi.dp(this, 286.0f)
        stage.addView(illustration, BrandUi.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                BrandUi.dp(this, 286.0f)
        ))
        val stageParams = BrandUi.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        )
        BrandUi.addTopMargin(stageParams, BrandUi.dp(this, 18.0f))
        content.addView(stage, stageParams)

        val title = BrandUi.text(this, page.title, 28.0f, getColor(R.color.camera_white), true)
        title.gravity = Gravity.CENTER
        title.setLineSpacing(0.0f, 1.08f)
        val titleParams = BrandUi.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        )
        BrandUi.addTopMargin(titleParams, BrandUi.dp(this, 6.0f))
        content.addView(title, titleParams)

        val body = BrandUi.text(this, page.body, 15.0f, getColor(R.color.camera_text_secondary), false)
        body.gravity = Gravity.CENTER
        body.setLineSpacing(0.0f, 1.22f)
        val bodyParams = BrandUi.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        )
        BrandUi.addTopMargin(bodyParams, BrandUi.dp(this, 12.0f))
        content.addView(body, bodyParams)

        val feature = LinearLayout(this)
        feature.gravity = Gravity.CENTER_VERTICAL
        feature.setPadding(
                BrandUi.dp(this, 14.0f),
                BrandUi.dp(this, 11.0f),
                BrandUi.dp(this, 14.0f),
                BrandUi.dp(this, 11.0f)
        )
        feature.background = BrandUi.outlined(
                0x33102A43,
                getColor(R.color.camera_line),
                BrandUi.dp(this, 16.0f).toFloat(),
                BrandUi.dp(this, 1.0f)
        )
        val featureIcon = ImageView(this)
        featureIcon.setImageResource(page.featureIcon)
        feature.addView(featureIcon, BrandUi.linearParams(
                BrandUi.dp(this, 28.0f),
                BrandUi.dp(this, 28.0f)
        ))
        val featureText = BrandUi.text(this, page.featureText, 12.0f, getColor(R.color.camera_white), true)
        val featureTextParams = BrandUi.linearParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
        BrandUi.addStartMargin(featureTextParams, BrandUi.dp(this, 10.0f))
        feature.addView(featureText, featureTextParams)
        val featureParams = BrandUi.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        )
        BrandUi.addTopMargin(featureParams, BrandUi.dp(this, 26.0f))
        content.addView(feature, featureParams)

        scroll.addView(content, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        return scroll
    }

    private fun createBottomBar(): View {
        val bar = LinearLayout(this)
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(0, BrandUi.dp(this, 12.0f), 0, 0)

        indicator = LinearLayout(this)
        indicator.gravity = Gravity.CENTER_VERTICAL
        bar.addView(indicator, BrandUi.linearParams(0, BrandUi.dp(this, 48.0f), 1.0f))
        for (index in pages.indices) {
            val dot = View(this)
            val dotParams = BrandUi.linearParams(BrandUi.dp(this, 8.0f), BrandUi.dp(this, 8.0f))
            BrandUi.addEndMargin(dotParams, BrandUi.dp(this, 7.0f))
            indicator.addView(dot, dotParams)
        }

        nextButton = BrandUi.text(this, getString(R.string.onboarding_next).uppercase(Locale.ROOT), 11.0f, getColor(R.color.camera_navy_deep), true)
        nextButton.letterSpacing = 0.08f
        nextButton.gravity = Gravity.CENTER
        nextButton.minHeight = BrandUi.dp(this, 48.0f)
        nextButton.setPadding(BrandUi.dp(this, 18.0f), 0, BrandUi.dp(this, 18.0f), 0)
        nextButton.setBackgroundResource(R.drawable.bg_gold_button)
        nextButton.setOnClickListener {
            if (currentPage == pages.size - 1) {
                finishOnboarding()
            } else {
                showPage(currentPage + 1, true)
            }
        }
        bar.addView(nextButton, BrandUi.linearParams(
                BrandUi.dp(this, 148.0f),
                BrandUi.dp(this, 48.0f)
        ))
        return bar
    }

    private fun showPage(page: Int, animate: Boolean) {
        val nextPage = Math.max(0, Math.min(page, pages.size - 1))
        val forward = nextPage > flipper.displayedChild
        currentPage = nextPage
        if (animate) {
            if (forward) {
                flipper.showNext()
            } else {
                flipper.showPrevious()
            }
        } else {
            flipper.displayedChild = currentPage
        }
        indicator.contentDescription = getString(
                R.string.onboarding_page_indicator,
                currentPage + 1,
                pages.size
        )
        for (index in 0 until indicator.childCount) {
            val dot = indicator.getChildAt(index)
            dot.background = BrandUi.rounded(
                    if (index == currentPage) getColor(R.color.camera_gold) else getColor(R.color.camera_line),
                    BrandUi.dp(this, 8.0f).toFloat()
            )
            val params = dot.layoutParams as LinearLayout.LayoutParams
            params.width = BrandUi.dp(this, if (index == currentPage) 22.0f else 8.0f)
            dot.layoutParams = params
        }
        nextButton.text = (
                if (currentPage == pages.size - 1) {
                    getString(R.string.onboarding_start)
                } else {
                    getString(R.string.onboarding_next)
                }
                ).uppercase(Locale.ROOT)
    }

    private fun finishOnboarding() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(ONBOARDING_COMPLETE, true)
                .apply()
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private fun getAnimation(resource: Int): Animation {
        val animation = AnimationUtils.loadAnimation(this, resource)
        animation.duration = 280L
        return animation
    }

    companion object {
        private const val PREFS_NAME = "srm_ap_event_camera"
        private const val ONBOARDING_COMPLETE = "onboarding_complete"
    }
}
