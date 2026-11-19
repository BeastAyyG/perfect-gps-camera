package com.perfectgps.camera.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.TextureView
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import com.perfectgps.camera.R
import com.perfectgps.camera.camera.CameraController
import com.perfectgps.camera.location.LocationController
import com.perfectgps.camera.media.PhotoRepository
import com.perfectgps.camera.model.GeoLocationSnapshot
import com.perfectgps.camera.util.BitmapDecodePolicy
import com.perfectgps.camera.util.GeoTextFormatter
import com.perfectgps.camera.util.LocationFreshnessPolicy
import com.perfectgps.camera.util.PhotoViewPolicy
import com.perfectgps.camera.util.PlaceNameResolver
import com.perfectgps.camera.util.PreviewGesturePolicy
import java.util.Collections
import java.util.Comparator
import java.util.LinkedHashSet
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : Activity(),
        CameraController.Callback,
        CameraController.JpegConsumer,
        LocationController.Callback,
        PhotoRepository.Callback {
    companion object {
        private const val PERMISSION_REQUEST_CODE = 41
        private const val PREFS_NAME = "srm_ap_event_camera"
        private const val PREFS_PERMISSIONS_ASKED = "permissions_asked"
        private const val MAX_LOCATION_AGE_MILLIS = 30_000L
        private const val TOP_CHROME_IDLE_MILLIS = 4_000L
        private const val FOCUS_RING_VISIBLE_MILLIS = 900L
        private const val ZOOM_EPSILON = 0.001f
        private val ZOOM_STEPS = floatArrayOf(1.0f, 1.5f, 2.0f, 3.0f, 5.0f, 10.0f)
        private const val QUICK_ZOOM_RATIO = 2.0f
        private const val FLASH_POP_MILLIS = 220L
        private const val CAPTURE_DRAIN_MILLIS = 1_500L
        private const val CAPTURE_FLASH_MILLIS = 240L
        private const val FLASH_POP_SCALE = 1.18f
        private const val SWIPE_SLOP_FACTOR = 3.0f
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideTopChromeRunnable = Runnable { hideTopChrome() }
    private val hideFocusRingRunnable = Runnable { hideFocusRing() }
    private val resetFlashPopRunnable = Runnable { resetFlashPop() }
    private val pendingPauseRunnable = Runnable { pauseCameraAfterDrain() }
    private val hideCaptureFlashRunnable = Runnable { hideCaptureFlash() }
    private val decodeExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var lastPreviewTapAtMillis: Long = 0L
    private var lastPreviewTapX: Float = 0.0f
    private var lastPreviewTapY: Float = 0.0f
    private var previewDownX: Float = 0.0f
    private var previewDownY: Float = 0.0f
    private var quickZoomRatio = QUICK_ZOOM_RATIO
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var previewView: TextureView? = null
    private var gridOverlay: GridOverlayView? = null
    private var flashButton: View? = null
    private var gridButton: View? = null
    private var lensButton: View? = null
    private var locationStatus: TextView? = null
    private var statusDetailGroup: LinearLayout? = null
    private var coordinatesText: TextView? = null
    private var detailsText: TextView? = null
    private var zoomText: TextView? = null
    private var captureButton: View? = null
    private var captureFlash: View? = null
    private var focusRing: View? = null
    private var galleryButton: ImageView? = null
    private var reviewScreen: FrameLayout? = null
    private var lastShotOverlay: ImageView? = null
    private var photoScaleDetector: ScaleGestureDetector? = null
    private var reviewScrim: View? = null
    private var reviewFooter: View? = null
    private var photoScale = 1.0f
    private var photoOffsetX: Float = 0.0f
    private var photoOffsetY: Float = 0.0f
    private var photoTouchX: Float = 0.0f
    private var photoTouchY: Float = 0.0f
    private var reviewCaption: TextView? = null
    private var shareSheet: FrameLayout? = null
    private var shareSheetRow: LinearLayout? = null
    private var shareSheetCaption: TextView? = null
    private var lastShotPreview: Bitmap? = null
    private var shotToken: Int = 0
    private var lastShotUri: Uri? = null
    private var lastShotDisplayName: String? = null
    private var lastShotCaption: String? = null
    private var lastShotHasLocation: Boolean = false
    private var topChrome: View? = null
    private var infoChrome: View? = null
    private var statusCardView: LinearLayout? = null
    private var topChromeVisible = true
    private var topBarView: View? = null
    private var permissionPanel: View? = null
    private var permissionTitle: TextView? = null
    private var permissionMessage: TextView? = null
    private var permissionButton: TextView? = null
    private var cameraController: CameraController? = null
    private var locationController: LocationController? = null
    private var placeNameResolver: PlaceNameResolver? = null
    private var photoRepository: PhotoRepository? = null
    private var currentLocation: GeoLocationSnapshot? = null
    private var pendingLocation: GeoLocationSnapshot? = null
    private var pendingCaptureTimeMillis: Long = 0L
    private var pendingFrontFacing: Boolean = false
    private var cameraReady: Boolean = false
    private var cameraHasFlash: Boolean = false
    private var cameraCanSwitchLens: Boolean = false
    private var gridEnabled = false
    private var saving: Boolean = false
    private var permissionRequestLaunched: Boolean = false
    private var appPaused: Boolean = false
    private var statusMessageActive: Boolean = false
    private val restoreLocationStatus = Runnable { clearStatusMessage() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeScaleGestureDetector()
        configureWindow()
        setContentView(createContentView())
        renderGrid()
        cameraController = CameraController(this, previewView!!, this)
        cameraController!!.setJpegConsumer(this)
        locationController = LocationController(this, this)
        placeNameResolver = PlaceNameResolver(this)
        photoRepository = PhotoRepository(this)
        previewView!!.setOnTouchListener { view, event ->
            showTopChrome()
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                previewDownX = event.x
                previewDownY = event.y
            } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                val consumed = handlePreviewSwipe(
                        view, event.x - previewDownX, event.y - previewDownY)
                if (!consumed && !handlePreviewTap(view, event.x, event.y)) {
                    view.performClick()
                }
            }
            scaleGestureDetector!!.onTouchEvent(event)
        }
        previewView!!.setOnClickListener { showTopChrome() }
        updatePermissionUi()
        if (missingPermissions().isNotEmpty() && !hasAskedForPermissions()) {
            requestMissingPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        appPaused = false
        mainHandler.removeCallbacks(pendingPauseRunnable)
        hideSystemUi()
        startAuthorizedComponents()
        updatePermissionUi()
    }

    override fun onPause() {
        appPaused = true
        mainHandler.removeCallbacks(hideTopChromeRunnable)
        mainHandler.removeCallbacks(hideFocusRingRunnable)
        mainHandler.removeCallbacks(resetFlashPopRunnable)
        mainHandler.removeCallbacks(hideCaptureFlashRunnable)
        resetFlashPop()
        hideFocusRing()
        hideCaptureFlash()
        hideShareSheet()
        hideLastShot()
        locationController!!.stop()
        if (saving) {
            mainHandler.postDelayed(pendingPauseRunnable, CAPTURE_DRAIN_MILLIS)
        } else {
            cameraController!!.pause()
        }
        super.onPause()
    }

    private fun pauseCameraAfterDrain() {
        if (appPaused) {
            cameraController!!.pause()
        }
    }


    override fun onDestroy() {
        mainHandler.removeCallbacks(hideTopChromeRunnable)
        mainHandler.removeCallbacks(hideFocusRingRunnable)
        mainHandler.removeCallbacks(resetFlashPopRunnable)
        mainHandler.removeCallbacks(hideCaptureFlashRunnable)
        mainHandler.removeCallbacks(pendingPauseRunnable)
        locationController!!.stop()
        placeNameResolver!!.close()
        cameraController!!.close()
        photoRepository!!.close()
        decodeExecutor.shutdownNow()
        shotToken++
        recycle(lastShotPreview)
        lastShotPreview = null
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
                && cameraController != null
                && cameraReady
                && !isPermissionPanelVisible()) {
            val next = stepZoom(if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 1 else -1)
            cameraController!!.setZoom(next)
            renderZoom()
            zoomText!!.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showTopChrome()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun stepZoom(direction: Int): Float {
        val current = cameraController!!.zoomRatio()
        var candidate = if (direction > 0) Float.MAX_VALUE else 0.0f
        for (step in ZOOM_STEPS) {
            if (direction > 0) {
                if (step > current + ZOOM_EPSILON && step < candidate) {
                    candidate = step
                }
            } else if (step < current - ZOOM_EPSILON && step > candidate) {
                candidate = step
            }
        }
        if (direction > 0) {
            return if (candidate == Float.MAX_VALUE) current else candidate
        }
        return if (candidate <= 0.0f) current else candidate
    }

    override fun onBackPressed() {
        if (shareSheet != null && shareSheet!!.visibility == View.VISIBLE) {
            hideShareSheet()
            return
        }
        if (reviewScreen != null && reviewScreen!!.visibility == View.VISIBLE) {
            hideLastShot()
            return
        }
        super.onBackPressed()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUi()
            showTopChrome()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configureWindow()
        showTopChrome()
    }

    private fun showTopChrome() {
        mainHandler.removeCallbacks(hideTopChromeRunnable)
        topChromeVisible = true
        revealChrome(topChrome)
        revealChrome(infoChrome)
        if (cameraReady && !saving) {
            mainHandler.postDelayed(hideTopChromeRunnable, TOP_CHROME_IDLE_MILLIS)
        }
    }

    private fun hideTopChrome() {
        if (!topChromeVisible) {
            return
        }
        if (!cameraReady || saving || isPermissionPanelVisible()) {
            mainHandler.postDelayed(hideTopChromeRunnable, TOP_CHROME_IDLE_MILLIS)
            return
        }
        topChromeVisible = false
        concealChrome(topChrome)
        concealChrome(infoChrome)
    }

    private fun revealChrome(view: View?) {
        if (view == null) {
            return
        }
        view.visibility = View.VISIBLE
        fadeIn(view)
    }

    private fun concealChrome(view: View?) {
        if (view == null) {
            return
        }
        view.animate()
                .alpha(0.0f)
                .setDuration(320L)
                .withEndAction { view.visibility = View.INVISIBLE }
                .start()
    }

    private fun isPermissionPanelVisible(): Boolean {
        return permissionPanel != null && permissionPanel!!.visibility == View.VISIBLE
    }

    private fun fadeIn(view: View?) {
        if (view == null || view.alpha >= 1.0f) {
            return
        }
        view.animate().alpha(1.0f).setDuration(280L).start()
    }

    private fun handlePreviewSwipe(view: View, dx: Float, dy: Float): Boolean {
        if (cameraController == null || !cameraReady || isPermissionPanelVisible()) {
            return false
        }
        if (flashButton == null || !flashButton!!.isEnabled) {
            return false
        }
        val configuration = ViewConfiguration.get(view.context)
        val direction = PreviewGesturePolicy.verticalSwipeDirection(
                dx, dy, configuration.scaledTouchSlop * SWIPE_SLOP_FACTOR)
        if (direction == PreviewGesturePolicy.DIRECTION_NONE) {
            return false
        }
        lastPreviewTapAtMillis = 0L
        applyFlashSwipe(direction)
        return true
    }

    private fun applyFlashSwipe(direction: Int) {
        val target = if (direction == PreviewGesturePolicy.DIRECTION_DOWN)
            CameraController.FLASH_ON
        else
            CameraController.FLASH_OFF
        cameraController!!.setFlashMode(target)
        renderFlash()
        popFlashButton()
        flashButton!!.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun popFlashButton() {
        if (flashButton == null) {
            return
        }
        mainHandler.removeCallbacks(resetFlashPopRunnable)
        flashButton!!.animate().cancel()
        flashButton!!.scaleX = FLASH_POP_SCALE
        flashButton!!.scaleY = FLASH_POP_SCALE
        flashButton!!.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(FLASH_POP_MILLIS)
                .start()
        mainHandler.postDelayed(resetFlashPopRunnable, FLASH_POP_MILLIS)
    }

    private fun resetFlashPop() {
        if (flashButton == null) {
            return
        }
        flashButton!!.scaleX = 1.0f
        flashButton!!.scaleY = 1.0f
    }

    private fun handlePreviewTap(view: View, x: Float, y: Float): Boolean {
        if (cameraController == null || !cameraReady || isPermissionPanelVisible()) {
            return false
        }
        val now = SystemClock.uptimeMillis()
        val configuration = ViewConfiguration.get(view.context)
        val slop = configuration.scaledTouchSlop * 4.0f
        val doubleTap = PreviewGesturePolicy.isDoubleTap(
                now - lastPreviewTapAtMillis, ViewConfiguration.getDoubleTapTimeout(),
                x - lastPreviewTapX, y - lastPreviewTapY, slop)
        if (doubleTap) {
            lastPreviewTapAtMillis = 0L
            applyQuickZoom()
            return true
        }
        lastPreviewTapAtMillis = now
        lastPreviewTapX = x
        lastPreviewTapY = y
        focusAtPoint(view, x, y)
        return false
    }

    private fun applyQuickZoom() {
        if (cameraController == null) {
            return
        }
        val current = cameraController!!.zoomRatio()
        if (current > 1.0f + ZOOM_EPSILON) {
            quickZoomRatio = current
        }
        cameraController!!.setZoom(if (current > 1.0f + ZOOM_EPSILON) 1.0f else quickZoomRatio)
        renderZoom()
        zoomText!!.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        showTopChrome()
    }

    private fun focusAtPoint(view: View, x: Float, y: Float) {
        showFocusRing(x, y)
        val width = view.width
        val height = view.height
        if (width == 0 || height == 0) {
            return
        }
        cameraController!!.focusAt(x, y, width, height)
    }

    private fun showFocusRing(x: Float, y: Float) {
        if (focusRing == null) {
            return
        }
        mainHandler.removeCallbacks(hideFocusRingRunnable)
        focusRing!!.animate().cancel()
        focusRing!!.alpha = 1.0f
        focusRing!!.scaleX = 1.45f
        focusRing!!.scaleY = 1.45f
        focusRing!!.visibility = View.VISIBLE
        val ringParams = focusRing!!.layoutParams
        focusRing!!.x = x - ringParams.width / 2.0f
        focusRing!!.y = y - ringParams.height / 2.0f
        focusRing!!.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200L).start()
        mainHandler.postDelayed(hideFocusRingRunnable, FOCUS_RING_VISIBLE_MILLIS)
    }

    private fun hideFocusRing() {
        if (focusRing == null) {
            return
        }
        focusRing!!.animate().cancel()
        focusRing!!.visibility = View.GONE
        focusRing!!.alpha = 0.0f
        focusRing!!.scaleX = 1.0f
        focusRing!!.scaleY = 1.0f
    }

    override fun onCameraReady(
            frontFacing: Boolean,
            flashAvailable: Boolean,
            canSwitchLens: Boolean
    ) {
        cameraReady = true
        cameraHasFlash = flashAvailable
        cameraCanSwitchLens = canSwitchLens
        renderFlash()
        renderLens()
        renderZoom()
        updateCaptureControls()
        showTopChrome()
    }

    override fun onCameraError(message: String) {
        cameraReady = false
        cameraHasFlash = false
        cameraCanSwitchLens = false
        updateCaptureControls()
        coordinatesText!!.setText(R.string.camera_unavailable)
        detailsText!!.text = message
        locationStatus!!.setText(R.string.camera_off)
        tintLocationPin(getColor(R.color.camera_chrome_muted))
        tintLocationPin(getColor(R.color.camera_chrome_muted))
    }

    override fun onLocationChanged(location: GeoLocationSnapshot?) {
        currentLocation = location
        if (location != null && location.isValid()) {
            placeNameResolver!!.request(location.latitude(), location.longitude())
        }
        renderLocationStatus()
    }

    override fun onLocationUnavailable() {
        renderLocationStatus()
    }

    override fun onJpegCaptured(jpeg: ByteArray) {
        if (jpeg.isEmpty()) {
            onSaveFailed(IllegalStateException("Empty JPEG"))
            return
        }
        rememberShot(jpeg)
        photoRepository!!.save(
                jpeg,
                pendingLocation,
                pendingCaptureTimeMillis,
                pendingFrontFacing,
                cameraController!!.lastJpegOrientation(),
                placeNameResolver!!.placeName(),
                if (pendingLocation == null)
                    ""
                else
                    GeoTextFormatter.formatCoordinates(pendingLocation, Locale.getDefault()),
                getString(R.string.watermark_no_fix),
                this
        )
    }

    override fun onSaved(photo: PhotoRepository.SavedPhoto?) {
        lastShotUri = photo!!.uri()
        lastShotDisplayName = photo.displayName()
        lastShotHasLocation = photo.hasLocation()
        lastShotCaption = buildShotCaption(photo.hasLocation())
        runOnUiThread {
            saving = false
            updateCaptureControls()
            showTopChrome()
            statusMessageActive = true
            locationStatus!!.setText(
                    if (photo!!.hasLocation()) R.string.saved_gps else R.string.saved_no_gps)
            tintLocationPin(getColor(
                    if (photo!!.hasLocation())
                        R.color.camera_accent
                    else
                        R.color.camera_gold_bright))
            mainHandler.removeCallbacks(restoreLocationStatus)
            mainHandler.postDelayed(restoreLocationStatus, 2_500L)
        }
    }

    override fun onSaveFailed(exception: Exception) {
        runOnUiThread {
            saving = false
            updateCaptureControls()
            showTopChrome()
            statusMessageActive = true
            locationStatus!!.setText(R.string.save_failed)
            tintLocationPin(getColor(R.color.camera_chrome_muted))
            mainHandler.removeCallbacks(restoreLocationStatus)
            mainHandler.postDelayed(restoreLocationStatus, 2_500L)
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            permissionRequestLaunched = false
            startAuthorizedComponents()
            updatePermissionUi()
        }
    }

    private fun initializeScaleGestureDetector() {
        scaleGestureDetector = ScaleGestureDetector(
                this,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        if (cameraController != null && cameraReady && !isPermissionPanelVisible()) {
                            cameraController!!.setZoom(
                                    cameraController!!.zoomRatio() * detector.scaleFactor)
                            renderZoom()
                        }
                        return true
                    }
                }
        )
    }

    private fun createContentView(): View {
        val root = FrameLayout(this)
        root.setBackgroundColor(getColor(R.color.camera_black))

        previewView = TextureView(this)
        previewView!!.id = R.id.preview
        previewView!!.contentDescription = getString(R.string.camera_preview)
        root.addView(previewView!!, matchParent())

        gridOverlay = GridOverlayView(this)
        gridOverlay!!.id = R.id.gridOverlay
        root.addView(gridOverlay!!, matchParent())

        topBarView = createTopBar()
        val topParams = FrameLayout.LayoutParams(
                ViewGroupLayout.MATCH_PARENT,
                ViewGroupLayout.WRAP_CONTENT,
                Gravity.TOP
        )
        topParams.setMargins(dp(16.0f), dp(44.0f), dp(16.0f), 0)
        root.addView(topBarView!!, topParams)

        val controls = createControls()
        val controlsParams = FrameLayout.LayoutParams(
                ViewGroupLayout.MATCH_PARENT,
                ViewGroupLayout.WRAP_CONTENT,
                Gravity.BOTTOM
        )
        controlsParams.setMargins(0, 0, 0, dp(18.0f))
        root.addView(controls, controlsParams)

        captureFlash = View(this)
        captureFlash!!.setBackgroundColor(getColor(R.color.camera_white))
        captureFlash!!.alpha = 0.0f
        captureFlash!!.visibility = View.GONE
        captureFlash!!.isClickable = false
        captureFlash!!.isFocusable = false
        captureFlash!!.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        root.addView(captureFlash!!, matchParent())

        focusRing = View(this)
        val focusRingShape = GradientDrawable()
        focusRingShape.setShape(GradientDrawable.OVAL)
        focusRingShape.setColor(0x00000000)
        focusRingShape.setStroke(dp(2.0f), getColor(R.color.camera_white))
        focusRing!!.background = focusRingShape
        focusRing!!.alpha = 0.0f
        focusRing!!.visibility = View.GONE
        focusRing!!.isClickable = false
        focusRing!!.isFocusable = false
        focusRing!!.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        val focusRingParams = FrameLayout.LayoutParams(
                dp(72.0f),
                dp(72.0f)
        )
        focusRingParams.gravity = Gravity.TOP or Gravity.START
        root.addView(focusRing!!, focusRingParams)

        topChrome = statusCardView
        infoChrome = statusDetailGroup
        topChrome!!.alpha = 0.0f
        infoChrome!!.alpha = 0.0f

        permissionPanel = createPermissionPanel()
        val permissionParams = FrameLayout.LayoutParams(
                ViewGroupLayout.MATCH_PARENT,
                ViewGroupLayout.WRAP_CONTENT,
                Gravity.CENTER
        )
        permissionParams.setMargins(dp(28.0f), 0, dp(28.0f), 0)
        root.addView(permissionPanel!!, permissionParams)

        reviewScreen = createReviewScreen()
        root.addView(reviewScreen!!, matchParent())

        shareSheet = createShareSheet()
        root.addView(shareSheet!!, matchParent())
        return root
    }

    private fun createReviewScreen(): FrameLayout {
        val screen = FrameLayout(this)
        screen.setBackgroundColor(getColor(R.color.camera_black))
        screen.visibility = View.GONE
        screen.isClickable = true
        screen.isFocusable = true
        screen.contentDescription = getString(R.string.last_shot_fullscreen)

        lastShotOverlay = ImageView(this)
        lastShotOverlay!!.scaleType = ImageView.ScaleType.MATRIX
        photoScaleDetector = ScaleGestureDetector(this, photoScaleListener)
        lastShotOverlay!!.setOnTouchListener { view, event ->
            photoScaleDetector!!.onTouchEvent(event)
            val action = event.actionMasked
            if (action == MotionEvent.ACTION_DOWN) {
                photoTouchX = event.x
                photoTouchY = event.y
            } else if (action == MotionEvent.ACTION_MOVE) {
                val dx = event.x - photoTouchX
                val dy = event.y - photoTouchY
                photoTouchX = event.x
                photoTouchY = event.y
                if (event.pointerCount == 1 && PhotoViewPolicy.isZoomed(photoScale)) {
                    photoOffsetX += dx
                    photoOffsetY += dy
                    applyPhotoTransform()
                }
            } else if (action == MotionEvent.ACTION_UP
                    && !PhotoViewPolicy.isZoomed(photoScale)) {
                view.performClick()
            }
            false
        }
        lastShotOverlay!!.setOnClickListener { hideLastShot() }
        screen.addView(lastShotOverlay!!, matchParent())

        val scrim = View(this)
        scrim.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x0005080D, 0x5905080D, 0xB305080D.toInt())
        )
        val scrimParams = FrameLayout.LayoutParams(
                ViewGroupLayout.MATCH_PARENT,
                dp(240.0f),
                Gravity.BOTTOM
        )
        screen.addView(scrim, scrimParams)

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.CENTER_HORIZONTAL
        column.setPadding(dp(16.0f), 0, dp(16.0f), 0)

        reviewCaption = label("", 10.0f, getColor(R.color.camera_chrome_secondary))
        reviewCaption!!.gravity = Gravity.CENTER
        reviewCaption!!.setSingleLine(true)
        reviewCaption!!.ellipsize = TextUtils.TruncateAt.MIDDLE
        reviewCaption!!.typeface = Typeface.MONOSPACE
        reviewCaption!!.letterSpacing = 0.04f
        column.addView(reviewCaption!!, LinearLayout.LayoutParams(
                ViewGroupLayout.MATCH_PARENT,
                ViewGroupLayout.WRAP_CONTENT
        ))

        val actions = LinearLayout(this)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.gravity = Gravity.CENTER
        val actionsParams = LinearLayout.LayoutParams(
                ViewGroupLayout.WRAP_CONTENT,
                ViewGroupLayout.WRAP_CONTENT
        )
        actionsParams.topMargin = dp(14.0f)
        column.addView(actions, actionsParams)

        actions.addView(
                reviewActionButton(
                        R.drawable.ic_share,
                        getString(R.string.share_photo),
                        getString(R.string.share_action)
                ) { shareLastShot() },
                square(74.0f, 20.0f, 0.0f)
        )
        actions.addView(
                reviewActionButton(
                        R.drawable.ic_delete,
                        getString(R.string.delete_photo),
                        getString(R.string.delete_action)
                ) { confirmDeleteLastShot() },
                square(74.0f, 0.0f, 20.0f)
        )

        val columnParams = FrameLayout.LayoutParams(
                ViewGroupLayout.MATCH_PARENT,
                ViewGroupLayout.WRAP_CONTENT,
                Gravity.BOTTOM
        )
        columnParams.setMargins(0, 0, 0, dp(26.0f))
        screen.addView(column, columnParams)
        reviewScrim = scrim
        reviewFooter = column
        return screen
    }

    private fun updateReviewChrome() {
        val zoomed = PhotoViewPolicy.isZoomed(photoScale)
        if (reviewScrim == null || reviewFooter == null) {
            return
        }
        val target = if (zoomed) 0.0f else 1.0f
        if (reviewScrim!!.alpha != target) {
            reviewScrim!!.animate().alpha(target).setDuration(180L).start()
        }
        if (reviewFooter!!.alpha != target) {
            reviewFooter!!.animate().alpha(target).setDuration(180L).start()
        }
        reviewFooter!!.isClickable = !zoomed
        reviewFooter!!.isFocusable = !zoomed
    }

    private fun square(size: Float, startMargin: Float, endMargin: Float): LinearLayout.LayoutParams {
        val params = LinearLayout.LayoutParams(dp(size), dp(size))
        params.leftMargin = dp(startMargin)
        params.rightMargin = dp(endMargin)
        return params
    }

    private fun reviewActionButton(
            iconResource: Int,
            description: String,
            caption: String,
            listener: View.OnClickListener
    ): View {
        val item = LinearLayout(this)
        item.orientation = LinearLayout.VERTICAL
        item.gravity = Gravity.CENTER_HORIZONTAL
        item.isClickable = true
        item.isFocusable = true
        item.contentDescription = description

        val button = ImageView(this)
        button.scaleType = ImageView.ScaleType.FIT_CENTER
        button.setPadding(dp(14.0f), dp(14.0f), dp(14.0f), dp(14.0f))
        button.background = roundedBackground(0x33FFFFFF, dp(26.0f).toFloat())
        button.setImageResource(iconResource)
        button.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        item.addView(button, LinearLayout.LayoutParams(
                dp(52.0f),
                dp(52.0f)
        ))

        val text = label(caption, 10.0f, getColor(R.color.camera_chrome_secondary))
        text.setSingleLine(true)
        text.ellipsize = TextUtils.TruncateAt.END
        val textParams = LinearLayout.LayoutParams(
                ViewGroupLayout.MATCH_PARENT,
                ViewGroupLayout.WRAP_CONTENT
        )
        textParams.topMargin = dp(6.0f)
        item.addView(text, textParams)

        item.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            listener.onClick(view)
        }
        return item
    }

    private fun createTopBar(): LinearLayout {
        val topBar = LinearLayout(this)
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.gravity = Gravity.TOP

        statusCardView = createStatusCard()
        topBar.addView(statusCardView!!, LinearLayout.LayoutParams(
                ViewGroupLayout.WRAP_CONTENT,
                ViewGroupLayout.WRAP_CONTENT
        ))

        topBar.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1.0f))

        flashButton = iconButton(R.id.flashButton, getString(R.string.flash_off), R.drawable.ic_flash_off)
        flashButton!!.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            cameraController!!.cycleFlash()
            renderFlash()
            popFlashButton()
        }
        topBar.addView(flashButton!!, square(dp(44.0f)))

        gridButton = iconButton(R.id.gridButton, getString(R.string.grid_on), R.drawable.ic_grid)
        gridButton!!.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            gridEnabled = !gridEnabled
            gridOverlay!!.isGridEnabled = gridEnabled
            renderGrid()
        }
        val gridParams = square(dp(44.0f))
        gridParams.marginStart = dp(8.0f)
        topBar.addView(gridButton!!, gridParams)

        lensButton = iconButton(R.id.lensButton, getString(R.string.lens_back), R.drawable.ic_switch_camera)
        lensButton!!.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            cameraController!!.switchLens()
            renderLens()
            renderZoom()
            updateCaptureControls()
        }
        val lensParams = square(dp(44.0f))
        lensParams.marginStart = dp(8.0f)
        topBar.addView(lensButton!!, lensParams)
        return topBar
    }

    private fun iconButton(id: Int, description: String, iconResource: Int): ImageView {
        val button = ImageView(this)
        button.id = id
        button.contentDescription = description
        button.scaleType = ImageView.ScaleType.FIT_CENTER
        button.setPadding(dp(11.0f), dp(11.0f), dp(11.0f), dp(11.0f))
        button.background = roundedBackground(0xA605080D.toInt(), dp(22.0f).toFloat())
        button.setImageResource(iconResource)
        return button
    }

    private fun applyIcon(view: View?, iconResource: Int) {
        if (view is ImageView) {
            view.setImageResource(iconResource)
        }
    }

    private fun tintLocationPin(color: Int) {
        if (locationStatus == null) {
            return
        }
        val icon = resources.getDrawable(R.drawable.ic_location_pin, theme)!!.mutate()
        icon.setTint(color)
        icon.setBounds(0, 0, dp(15.0f), dp(15.0f))
        locationStatus!!.setCompoundDrawables(icon, null, null, null)
    }

    private fun setIconAlpha(view: View?, alpha: Float) {
        if (view == null) {
            return
        }
        if (view !is ImageView) {
            view.alpha = alpha
            return
        }
        val image: ImageView = view
        val icon = image.drawable ?: return
        val faded = icon.mutate()
        faded.alpha = Math.round(alpha * 255.0f)
        image.setImageDrawable(faded)
        image.alpha = 1.0f
    }

    private fun applyIcon(view: TextView, iconResource: Int, size: Int, leading: Boolean) {
        val icon = getDrawable(iconResource) ?: return
        icon.setBounds(0, 0, size, size)
        if (leading) {
            view.setCompoundDrawables(icon, null, null, null)
        } else {
            view.setCompoundDrawables(null, icon, null, null)
        }
    }

    private fun createStatusCard(): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(12.0f), dp(7.0f), dp(14.0f), dp(8.0f))
        card.background = roundedBackground(0xA605080D.toInt(), dp(14.0f).toFloat())

        locationStatus = label(getString(R.string.waiting_for_gps), 11.0f, getColor(R.color.camera_white))
        locationStatus!!.id = R.id.locationStatus
        locationStatus!!.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        locationStatus!!.letterSpacing = 0.04f
        locationStatus!!.gravity = Gravity.CENTER_VERTICAL
        locationStatus!!.setSingleLine(true)
        locationStatus!!.ellipsize = TextUtils.TruncateAt.END
        locationStatus!!.compoundDrawablePadding = dp(6.0f)
        locationStatus!!.contentDescription = getString(R.string.location_status_description)
        locationStatus!!.setOnClickListener {
            if (!hasLocationPermission()) {
                if (canAskForLocation()) {
                    requestMissingPermissions()
                } else {
                    openAppSettings()
                }
                return@setOnClickListener
            }
            if (currentLocation == null) {
                return@setOnClickListener
            }
            statusMessageActive = true
            locationStatus!!.text = GeoTextFormatter.formatAccuracy(
                    currentLocation, Locale.getDefault())
            mainHandler.removeCallbacks(restoreLocationStatus)
            mainHandler.postDelayed(restoreLocationStatus, 3_000L)
        }
        applyIcon(locationStatus!!, R.drawable.ic_location_pin, dp(15.0f), true)
        card.addView(locationStatus!!, LinearLayout.LayoutParams(
                ViewGroupLayout.WRAP_CONTENT,
                ViewGroupLayout.WRAP_CONTENT
        ))

        val detail = LinearLayout(this)
        detail.orientation = LinearLayout.VERTICAL
        detail.setPadding(0, dp(6.0f), 0, 0)
        statusDetailGroup = detail

        coordinatesText = label("", 11.5f, getColor(R.color.camera_white))
        coordinatesText!!.id = R.id.coordinatesText
        coordinatesText!!.typeface = Typeface.MONOSPACE
        coordinatesText!!.setSingleLine(true)
        coordinatesText!!.ellipsize = TextUtils.TruncateAt.END
        coordinatesText!!.visibility = View.GONE
        detail.addView(coordinatesText!!, LinearLayout.LayoutParams(
                ViewGroupLayout.WRAP_CONTENT,
                ViewGroupLayout.WRAP_CONTENT
        ))

        detailsText = label(getString(R.string.coordinates_waiting_detail), 9.0f, getColor(R.color.camera_chrome_muted))
        detailsText!!.id = R.id.detailsText
        detailsText!!.setSingleLine(true)
        detailsText!!.ellipsize = TextUtils.TruncateAt.END
        val detailsParams = LinearLayout.LayoutParams(
                ViewGroupLayout.WRAP_CONTENT,
                ViewGroupLayout.WRAP_CONTENT
        )
        detailsParams.topMargin = dp(2.0f)
        detail.addView(detailsText!!, detailsParams)

        card.addView(detail, LinearLayout.LayoutParams(
                ViewGroupLayout.WRAP_CONTENT,
                ViewGroupLayout.WRAP_CONTENT
        ))
        return card
    }

    private fun createControls(): FrameLayout {
        val controls = FrameLayout(this)

        val dock = LinearLayout(this)
        dock.orientation = LinearLayout.VERTICAL
        dock.gravity = Gravity.CENTER_HORIZONTAL
        dock.setPadding(dp(16.0f), dp(24.0f), dp(16.0f), dp(12.0f))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER

        galleryButton = ImageView(this)
        galleryButton!!.scaleType = ImageView.ScaleType.CENTER_CROP
        galleryButton!!.background = roundedBackground(0xA605080D.toInt(), dp(10.0f).toFloat())
        galleryButton!!.clipToOutline = true
        galleryButton!!.visibility = View.INVISIBLE
        galleryButton!!.isClickable = true
        galleryButton!!.isFocusable = true
        galleryButton!!.contentDescription = getString(R.string.last_shot)
        galleryButton!!.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            openLastShot()
        }
        val galleryParams = square(dp(48.0f))
        galleryParams.marginEnd = dp(44.0f)
        row.addView(galleryButton!!, galleryParams)

        val shutterRing = FrameLayout(this)
        shutterRing.id = R.id.captureButton
        captureButton = shutterRing
        shutterRing.contentDescription = getString(R.string.capture_photo)
        shutterRing.isClickable = true
        shutterRing.isFocusable = true
        val ring = GradientDrawable()
        ring.setShape(GradientDrawable.OVAL)
        ring.setColor(0x1A000000)
        ring.setStroke(dp(4.0f), 0x99000000.toInt())
        shutterRing.background = ring
        val shutter = View(this)
        val shutterBackground = GradientDrawable()
        shutterBackground.setShape(GradientDrawable.OVAL)
        shutterBackground.setColor(getColor(R.color.camera_white))
        shutterBackground.setStroke(dp(2.0f), 0x80000000.toInt())
        shutter.background = shutterBackground
        shutterRing.addView(shutter, FrameLayout.LayoutParams(dp(64.0f), dp(64.0f), Gravity.CENTER))
        shutterRing.setOnClickListener { capturePhoto() }
        shutterRing.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    view.animate().scaleX(0.88f).scaleY(0.88f).setDuration(90L).start()
                } else {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160L).start()
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        view.performClick()
                    }
                }
                return false
            }
        })
        row.addView(shutterRing, square(dp(80.0f)))

        zoomText = label(getString(R.string.zoom_compact, 1.0f), 11.0f, getColor(R.color.camera_white))
        zoomText!!.id = R.id.zoomText
        zoomText!!.gravity = Gravity.CENTER
        zoomText!!.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
        zoomText!!.letterSpacing = 0.04f
        zoomText!!.setSingleLine(true)
        zoomText!!.background = pillBackground(0x73000000)
        zoomText!!.setShadowLayer(dp(3.0f).toFloat(), 0.0f, dp(1.0f).toFloat(), 0xB3000000.toInt())
        zoomText!!.contentDescription = getString(R.string.zoom_control)
        zoomText!!.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            cameraController!!.setZoom(1.0f)
            renderZoom()
        }
        val zoomParams = LinearLayout.LayoutParams(dp(68.0f), dp(36.0f))
        zoomParams.marginStart = dp(24.0f)
        row.addView(zoomText!!, zoomParams)

        dock.addView(row, LinearLayout.LayoutParams(
                ViewGroupLayout.MATCH_PARENT,
                ViewGroupLayout.WRAP_CONTENT
        ))

        val homeIndicator = View(this)
        val indicator = GradientDrawable()
        indicator.cornerRadius = dp(3.0f).toFloat()
        indicator.setColor(0x59FFFFFF)
        homeIndicator.background = indicator
        val indicatorParams = LinearLayout.LayoutParams(dp(112.0f), dp(4.0f))
        indicatorParams.topMargin = dp(16.0f)
        dock.addView(homeIndicator, indicatorParams)

        controls.addView(dock, FrameLayout.LayoutParams(
                ViewGroupLayout.MATCH_PARENT,
                ViewGroupLayout.WRAP_CONTENT
        ))
        return controls
    }

    private fun createPermissionPanel(): View {
        val panel = LinearLayout(this)
        panel.id = R.id.permissionPanel
        panel.orientation = LinearLayout.VERTICAL
        panel.gravity = Gravity.CENTER_HORIZONTAL
        panel.setPadding(dp(26.0f), dp(24.0f), dp(26.0f), dp(22.0f))
        panel.background = roundedBackground(0xF205080D.toInt(), dp(22.0f).toFloat())

        val lock = ImageView(this)
        lock.setImageResource(R.drawable.ic_lock)
        lock.contentDescription = getString(R.string.permission_badge)
        panel.addView(lock, LinearLayout.LayoutParams(dp(30.0f), dp(30.0f)))

        val badge = label(getString(R.string.permission_badge), 8.5f, getColor(R.color.camera_chrome_muted))
        badge.gravity = Gravity.CENTER
        badge.letterSpacing = 0.15f
        badge.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val badgeParams = wrap(0)
        badgeParams.topMargin = dp(9.0f)
        panel.addView(badge, badgeParams)

        permissionTitle = label(getString(R.string.permission_camera_title), 19.0f, getColor(R.color.camera_white))
        permissionTitle!!.id = R.id.permissionTitle
        permissionTitle!!.gravity = Gravity.CENTER
        permissionTitle!!.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val titleParams = wrap(0)
        titleParams.topMargin = dp(10.0f)
        panel.addView(permissionTitle!!, titleParams)

        permissionMessage = label(getString(R.string.permission_camera_message), 12.5f, getColor(R.color.camera_chrome_secondary))
        permissionMessage!!.id = R.id.permissionMessage
        permissionMessage!!.gravity = Gravity.CENTER
        permissionMessage!!.setLineSpacing(0.0f, 1.2f)
        val messageParams = wrap(0)
        messageParams.topMargin = dp(8.0f)
        panel.addView(permissionMessage!!, messageParams)

        permissionButton = pillButton(
                getString(R.string.grant_access_label),
                R.id.permissionButton,
                getString(R.string.grant_access)
        )
        permissionButton!!.setTextColor(getColor(R.color.camera_navy_deep))
        permissionButton!!.background = pillBackground(getColor(R.color.camera_white))
        permissionButton!!.setOnClickListener { onPermissionAction() }
        val buttonParams = LinearLayout.LayoutParams(
                ViewGroupLayout.MATCH_PARENT,
                dp(48.0f)
        )
        buttonParams.topMargin = dp(18.0f)
        panel.addView(permissionButton!!, buttonParams)

        val footnote = label(getString(R.string.permission_footnote), 10.0f, getColor(R.color.camera_chrome_muted))
        footnote.gravity = Gravity.CENTER
        footnote.setLineSpacing(0.0f, 1.15f)
        val footnoteParams = wrap(0)
        footnoteParams.topMargin = dp(12.0f)
        panel.addView(footnote, footnoteParams)
        panel.visibility = View.GONE
        return panel
    }

    private fun pillButton(
            text: String,
            id: Int,
            description: String
    ): TextView {
        return pillButton(text, id, description, 0)
    }

    private fun pillButton(
            text: String,
            id: Int,
            description: String,
            iconResource: Int
    ): TextView {
        val button = label(text, 10.0f, getColor(R.color.camera_white))
        button.id = id
        button.contentDescription = description
        button.gravity = Gravity.CENTER
        button.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        button.letterSpacing = 0.05f
        button.setMinHeight(dp(40.0f))
        button.setPadding(dp(8.0f), 0, dp(8.0f), 0)
        button.background = outlinedBackground(
                0xCC07182D.toInt(), getColor(R.color.camera_line), dp(20.0f).toFloat(), dp(1.0f))
        if (iconResource != 0) {
            val icon = getDrawable(iconResource)
            if (icon != null) {
                icon.setBounds(0, 0, dp(16.0f), dp(16.0f))
                button.setCompoundDrawables(null, null, icon, null)
                button.compoundDrawablePadding = dp(5.0f)
            }
        }
        return button
    }

    private fun renderGrid() {
        if (gridButton != null) {
            setIconAlpha(gridButton, if (gridEnabled) 1.0f else 0.45f)
            gridButton!!.contentDescription = if (gridEnabled)
                getString(R.string.grid_on)
            else
                getString(R.string.grid_off)
        }
    }

    private fun capturePhoto() {
        if (!cameraReady || saving || !hasRequiredStoragePermission()) {
            return
        }
        pendingLocation = if (LocationFreshnessPolicy.isFresh(
                        currentLocation,
                        System.currentTimeMillis(),
                        MAX_LOCATION_AGE_MILLIS
                )) currentLocation else null
        pendingCaptureTimeMillis = System.currentTimeMillis()
        pendingFrontFacing = cameraController!!.isFrontFacing()
        saving = true
        updateCaptureControls()
        locationStatus!!.setText(if (pendingLocation == null)
            R.string.capture_without_gps
        else
            R.string.capture_with_gps)
        tintLocationPin(getColor(if (pendingLocation == null)
            R.color.camera_gold_bright
        else
            R.color.camera_accent))
        previewView!!.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        flashCaptureOverlay()
        showTopChrome()
        cameraController!!.capture()
    }

    private fun flashCaptureOverlay() {
        if (captureFlash == null) {
            return
        }
        mainHandler.removeCallbacks(hideCaptureFlashRunnable)
        captureFlash!!.animate().cancel()
        captureFlash!!.alpha = 0.82f
        captureFlash!!.visibility = View.VISIBLE
        captureFlash!!.animate()
                .alpha(0.0f)
                .setDuration(CAPTURE_FLASH_MILLIS)
                .start()
        mainHandler.postDelayed(hideCaptureFlashRunnable, CAPTURE_FLASH_MILLIS)
    }

    private fun hideCaptureFlash() {
        if (captureFlash == null) {
            return
        }
        captureFlash!!.animate().cancel()
        captureFlash!!.visibility = View.GONE
        captureFlash!!.alpha = 0.0f
    }

    private fun rememberShot(jpeg: ByteArray) {
        val token = ++shotToken
        val previewTarget = BitmapDecodePolicy.previewTarget(
                resources.displayMetrics.widthPixels
        )
        val thumbnailTarget = dp(96.0f)
        val degrees = cameraController!!.lastJpegOrientation()
        val mirrored = cameraController!!.isFrontFacing()
        decodeExecutor.execute {
            val preview = orient(decodeScaled(jpeg, previewTarget), degrees, mirrored)
            val thumbnail = orient(decodeScaled(jpeg, thumbnailTarget), degrees, mirrored)
            runOnUiThread {
                if (token != shotToken || isFinishing() || isDestroyed()) {
                    recycle(preview)
                    recycle(thumbnail)
                    return@runOnUiThread
                }
                val previous = lastShotPreview
                lastShotPreview = preview
                if (previous !== preview) {
                    recycle(previous)
                }
                if (thumbnail == null || galleryButton == null) {
                    return@runOnUiThread
                }
                val previousThumbnail = galleryButton!!.drawable
                galleryButton!!.setImageBitmap(thumbnail)
                recycleDrawable(previousThumbnail)
                galleryButton!!.visibility = View.VISIBLE
                galleryButton!!.alpha = 0.0f
                galleryButton!!.animate().alpha(1.0f).setDuration(240L).start()
            }
        }
    }

    private fun recycle(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    private fun recycleDrawable(drawable: Drawable?) {
        if (drawable is BitmapDrawable && drawable.intrinsicWidth > 0) {
            recycle(drawable.bitmap)
        }
    }

    private fun orient(bitmap: Bitmap?, degrees: Int, mirrored: Boolean): Bitmap? {
        if (bitmap == null) {
            return null
        }
        val matrix = Matrix()
        if (mirrored) {
            matrix.setScale(-1.0f, 1.0f)
        }
        if (degrees != 0) {
            matrix.postRotate(degrees.toFloat())
        }
        if (matrix.isIdentity) {
            return bitmap
        }
        val oriented = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
        )
        if (oriented !== bitmap) {
            bitmap.recycle()
        }
        return oriented
    }

    private fun decodeScaled(data: ByteArray, target: Int): Bitmap? {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val longest = Math.max(bounds.outWidth, bounds.outHeight)
        val sample = BitmapDecodePolicy.sampleSize(longest, target)
        val options = BitmapFactory.Options()
        options.inSampleSize = sample
        return BitmapFactory.decodeByteArray(data, 0, data.size, options)
    }

    private fun openLastShot() {
        if (lastShotPreview == null || reviewScreen == null) {
            return
        }
        hideTopChrome()
        reviewScreen!!.animate().cancel()
        lastShotOverlay!!.animate().cancel()
        lastShotOverlay!!.setImageBitmap(lastShotPreview)
        lastShotOverlay!!.alpha = 1.0f
        photoScale = PhotoViewPolicy.MIN_SCALE
        photoOffsetX = 0.0f
        photoOffsetY = 0.0f
        reviewCaption!!.text = lastShotCaption ?: ""
        reviewScreen!!.visibility = View.VISIBLE
        reviewScreen!!.bringToFront()
        lastShotOverlay!!.post { applyPhotoTransform() }
    }

    private fun buildShotCaption(hasLocation: Boolean): String {
        val time = android.text.format.DateFormat
                .getTimeFormat(this)
                .format(java.util.Date(pendingCaptureTimeMillis))
        if (!hasLocation || pendingLocation == null) {
            return getString(R.string.review_caption_no_fix, time, getString(R.string.review_no_gps))
        }
        return getString(
                R.string.review_caption,
                time,
                GeoTextFormatter.formatCoordinates(pendingLocation, Locale.getDefault()),
                getString(R.string.review_gps_tag)
        )
    }

    private val photoScaleListener: ScaleGestureDetector.SimpleOnScaleGestureListener =
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val next = PhotoViewPolicy.clampScale(
                            photoScale * detector.scaleFactor
                    )
                    photoOffsetX = PhotoViewPolicy.focusOffset(
                            detector.focusX, photoOffsetX, photoScale, next
                    )
                    photoOffsetY = PhotoViewPolicy.focusOffset(
                            detector.focusY, photoOffsetY, photoScale, next
                    )
                    photoScale = next
                    if (!PhotoViewPolicy.isZoomed(photoScale)) {
                        photoScale = PhotoViewPolicy.MIN_SCALE
                        photoOffsetX = 0.0f
                        photoOffsetY = 0.0f
                    }
                    applyPhotoTransform()
                    return true
                }
            }

    private fun applyPhotoTransform() {
        if (lastShotOverlay == null || lastShotOverlay!!.drawable == null) {
            return
        }
        val viewWidth = lastShotOverlay!!.width
        val viewHeight = lastShotOverlay!!.height
        if (viewWidth <= 0 || viewHeight <= 0) {
            return
        }
        val drawableWidth = lastShotOverlay!!.drawable!!.intrinsicWidth
        val drawableHeight = lastShotOverlay!!.drawable!!.intrinsicHeight
        val fit = PhotoViewPolicy.fitScale(
                viewWidth, viewHeight, drawableWidth, drawableHeight
        )
        val shownWidth = PhotoViewPolicy.shownSize(drawableWidth, fit)
        val shownHeight = PhotoViewPolicy.shownSize(drawableHeight, fit)
        photoOffsetX = PhotoViewPolicy.clampOffset(
                photoOffsetX, viewWidth, shownWidth, photoScale
        )
        photoOffsetY = PhotoViewPolicy.clampOffset(
                photoOffsetY, viewHeight, shownHeight, photoScale
        )
        val matrix = Matrix()
        matrix.setScale(fit, fit)
        matrix.postTranslate(
                (viewWidth - shownWidth) / 2.0f + photoOffsetX,
                (viewHeight - shownHeight) / 2.0f + photoOffsetY
        )
        matrix.postScale(photoScale, photoScale, viewWidth / 2.0f, viewHeight / 2.0f)
        lastShotOverlay!!.imageMatrix = matrix
        updateReviewChrome()
    }

    private fun hideLastShot() {
        if (reviewScreen == null || reviewScreen!!.visibility != View.VISIBLE) {
            return
        }
        reviewScreen!!.animate().cancel()
        lastShotOverlay!!.animate().cancel()
        reviewScreen!!.visibility = View.GONE
        lastShotOverlay!!.setImageDrawable(null)
        lastShotOverlay!!.alpha = 0.0f
        photoScale = PhotoViewPolicy.MIN_SCALE
        photoOffsetX = 0.0f
        photoOffsetY = 0.0f
        if (reviewScrim != null) {
            reviewScrim!!.animate().cancel()
            reviewScrim!!.alpha = 1.0f
        }
        if (reviewFooter != null) {
            reviewFooter!!.animate().cancel()
            reviewFooter!!.alpha = 1.0f
            reviewFooter!!.isClickable = true
            reviewFooter!!.isFocusable = true
        }
    }

    private fun shareLastShot() {
        if (lastShotUri == null) {
            return
        }
        if (shareSheet == null) {
            showStatusMessage(getString(R.string.share_unavailable), R.color.camera_gold_bright)
            return
        }
        hideTopChrome()
        shareSheetCaption!!.text = lastShotCaption ?: ""
        populateShareTargets()
        shareSheet!!.animate().cancel()
        shareSheet!!.visibility = View.VISIBLE
        shareSheet!!.bringToFront()
    }

    private fun hideShareSheet() {
        if (shareSheet == null || shareSheet!!.visibility != View.VISIBLE) {
            return
        }
        shareSheet!!.animate().cancel()
        shareSheet!!.visibility = View.GONE
    }

    private fun createShareSheet(): FrameLayout {
        val sheet = FrameLayout(this)
        sheet.setBackgroundColor(0x99000000.toInt())
        sheet.visibility = View.GONE
        sheet.isClickable = true
        sheet.isFocusable = true
        sheet.contentDescription = getString(R.string.share_close)
        sheet.setOnClickListener { hideShareSheet() }

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(dp(16.0f), dp(18.0f), dp(16.0f), dp(30.0f))
        column.background = topSheetBackground(0xFF05080D.toInt(), dp(20.0f).toFloat())

        val kicker = label(getString(R.string.share_targets), 10.0f, getColor(R.color.camera_chrome_muted))
        kicker.typeface = Typeface.MONOSPACE
        kicker.letterSpacing = 0.16f
        column.addView(kicker, LinearLayout.LayoutParams(
                ViewGroupLayout.MATCH_PARENT,
                ViewGroupLayout.WRAP_CONTENT
        ))

        shareSheetCaption = label("", 10.0f, getColor(R.color.camera_chrome_secondary))
        shareSheetCaption!!.typeface = Typeface.MONOSPACE
        shareSheetCaption!!.setSingleLine(true)
        shareSheetCaption!!.ellipsize = TextUtils.TruncateAt.MIDDLE
        val captionParams = LinearLayout.LayoutParams(
                ViewGroupLayout.MATCH_PARENT,
                ViewGroupLayout.WRAP_CONTENT
        )
        captionParams.topMargin = dp(5.0f)
        column.addView(shareSheetCaption!!, captionParams)

        shareSheetRow = LinearLayout(this)
        shareSheetRow!!.orientation = LinearLayout.HORIZONTAL
        shareSheetRow!!.gravity = Gravity.CENTER
        val rowParams = LinearLayout.LayoutParams(
                ViewGroupLayout.MATCH_PARENT,
                ViewGroupLayout.WRAP_CONTENT
        )
        rowParams.topMargin = dp(18.0f)
        column.addView(shareSheetRow!!, rowParams)

        sheet.addView(column, FrameLayout.LayoutParams(
                ViewGroupLayout.MATCH_PARENT,
                ViewGroupLayout.WRAP_CONTENT,
                Gravity.BOTTOM
        ))
        return sheet
    }

    private fun populateShareTargets() {
        if (shareSheetRow == null || lastShotUri == null) {
            return
        }
        shareSheetRow!!.removeAllViews()
        val resolved = packageManager.queryIntentActivities(shareIntent(), 0)
        Collections.sort(resolved, object : Comparator<ResolveInfo> {
            override fun compare(left: ResolveInfo, right: ResolveInfo): Int {
                val leftSystem = isSystemApp(left)
                val rightSystem = isSystemApp(right)
                if (leftSystem != rightSystem) {
                    return if (leftSystem) -1 else 1
                }
                return left.loadLabel(packageManager).toString()
                        .compareTo(right.loadLabel(packageManager).toString(), ignoreCase = true)
            }
        })
        var index = 0
        for (info in resolved) {
            if (index >= 3) {
                break
            }
            shareSheetRow!!.addView(shareTargetView(
                    info.loadLabel(packageManager),
                    info.loadIcon(packageManager)
            ) { launchShare(shareIntentFor(info)) }, shareTargetParams(index))
            index++
        }
        if (index == 0) {
            val empty = label(
                    getString(R.string.share_no_apps),
                    11.5f,
                    getColor(R.color.camera_chrome_muted)
            )
            empty.gravity = Gravity.CENTER
            shareSheetRow!!.addView(empty, LinearLayout.LayoutParams(
                    ViewGroupLayout.MATCH_PARENT,
                    ViewGroupLayout.WRAP_CONTENT
            ))
            return
        }
        shareSheetRow!!.addView(shareTargetView(
                getString(R.string.share_more),
                getDrawable(R.drawable.ic_gallery)
        ) { showSystemChooser() }, shareTargetParams(index))
    }

    private fun isSystemApp(info: ResolveInfo): Boolean {
        return info.activityInfo != null
                && info.activityInfo.applicationInfo != null
                && (info.activityInfo.applicationInfo.flags
                and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
    }

    private fun shareTargetParams(index: Int): LinearLayout.LayoutParams {
        val params = LinearLayout.LayoutParams(dp(84.0f), ViewGroupLayout.WRAP_CONTENT)
        params.leftMargin = dp(4.0f)
        params.rightMargin = dp(4.0f)
        if (index == 0) {
            params.leftMargin = 0
        }
        return params
    }

    private fun shareTargetView(name: CharSequence, icon: Drawable?, listener: View.OnClickListener): View {
        val item = LinearLayout(this)
        item.orientation = LinearLayout.VERTICAL
        item.gravity = Gravity.CENTER_HORIZONTAL

        val badge = ImageView(this)
        badge.setImageDrawable(icon)
        badge.scaleType = ImageView.ScaleType.FIT_CENTER
        badge.setPadding(dp(13.0f), dp(13.0f), dp(13.0f), dp(13.0f))
        badge.background = roundedBackground(0x33FFFFFF, dp(27.0f).toFloat())
        badge.contentDescription = name
        item.addView(badge, LinearLayout.LayoutParams(dp(54.0f), dp(54.0f)))

        val caption = label(name.toString(), 10.0f, getColor(R.color.camera_white))
        caption.gravity = Gravity.CENTER
        caption.setSingleLine(true)
        caption.ellipsize = TextUtils.TruncateAt.END
        val captionParams = LinearLayout.LayoutParams(
                ViewGroupLayout.MATCH_PARENT,
                ViewGroupLayout.WRAP_CONTENT
        )
        captionParams.topMargin = dp(7.0f)
        item.addView(caption, captionParams)

        item.contentDescription = name
        item.isClickable = true
        item.isFocusable = true
        item.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            listener.onClick(view)
        }
        return item
    }

    private fun shareIntent(): Intent {
        val share = Intent(Intent.ACTION_SEND)
        share.type = "image/jpeg"
        share.putExtra(Intent.EXTRA_STREAM, lastShotUri!!)
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (lastShotDisplayName != null) {
            share.putExtra(Intent.EXTRA_SUBJECT, lastShotDisplayName!!)
        }
        return share
    }

    private fun shareIntentFor(info: ResolveInfo): Intent {
        val share = shareIntent()
        share.setClassName(info.activityInfo.packageName, info.activityInfo.name)
        return share
    }

    private fun launchShare(share: Intent) {
        hideShareSheet()
        try {
            startActivity(share)
        } catch (exception: android.content.ActivityNotFoundException) {
            showStatusMessage(getString(R.string.share_unavailable), R.color.camera_gold_bright)
        }
    }

    private fun showSystemChooser() {
        hideShareSheet()
        try {
            startActivity(Intent.createChooser(shareIntent(), getString(R.string.share_photo)))
        } catch (exception: android.content.ActivityNotFoundException) {
            showStatusMessage(getString(R.string.share_unavailable), R.color.camera_gold_bright)
        }
    }

    private fun confirmDeleteLastShot() {
        if (lastShotUri == null) {
            return
        }
        AlertDialog.Builder(this)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(R.string.delete_confirm_message)
                .setNegativeButton(R.string.delete_confirm_cancel, null)
                .setPositiveButton(R.string.delete_confirm_action) { _, _ -> deleteLastShot() }
                .show()
    }

    private fun deleteLastShot() {
        if (lastShotUri == null) {
            return
        }
        val removed: Int = try {
            contentResolver.delete(lastShotUri!!, null, null)
        } catch (exception: SecurityException) {
            0
        }
        hideLastShot()
        if (removed > 0) {
            shotToken++
            recycle(lastShotPreview)
            lastShotPreview = null
            lastShotUri = null
            lastShotDisplayName = null
            lastShotHasLocation = false
            val thumbnail = galleryButton!!.drawable
            galleryButton!!.setImageDrawable(null)
            recycleDrawable(thumbnail)
            galleryButton!!.visibility = View.INVISIBLE
            showStatusMessage(getString(R.string.photo_deleted), R.color.camera_chrome_secondary)
        } else {
            showStatusMessage(getString(R.string.photo_delete_failed), R.color.camera_gold_bright)
        }
    }

    private fun showStatusMessage(message: String, pinColor: Int) {
        if (locationStatus == null) {
            return
        }
        statusMessageActive = true
        locationStatus!!.text = message
        tintLocationPin(getColor(pinColor))
        mainHandler.removeCallbacks(restoreLocationStatus)
        mainHandler.postDelayed(restoreLocationStatus, 2_500L)
        showTopChrome()
    }

    private fun startAuthorizedComponents() {
        if (cameraController != null && hasCameraPermission()) {
            cameraController!!.start()
        }
        if (locationController != null && hasLocationPermission()) {
            locationController!!.start()
        }
    }

    private fun clearStatusMessage() {
        statusMessageActive = false
        renderLocationStatus()
    }

    private fun renderLocationStatus() {
        if (statusMessageActive) {
            return
        }
        if (!hasLocationPermission()) {
            locationStatus!!.setText(R.string.location_off)
            tintLocationPin(getColor(R.color.camera_chrome_muted))
            coordinatesText!!.visibility = View.GONE
            detailsText!!.setText(R.string.coordinates_enable_detail)
            return
        }
        val fresh = LocationFreshnessPolicy.isFresh(
                currentLocation,
                System.currentTimeMillis(),
                MAX_LOCATION_AGE_MILLIS
        )
        if (!fresh) {
            locationStatus!!.setText(R.string.waiting_for_gps)
            tintLocationPin(getColor(R.color.camera_gold_bright))
            coordinatesText!!.visibility = View.GONE
            detailsText!!.setText(R.string.coordinates_waiting_detail)
            return
        }
        coordinatesText!!.visibility = View.VISIBLE
        locationStatus!!.text = getString(
                R.string.gps_accuracy,
                GeoTextFormatter.formatAccuracy(currentLocation, Locale.getDefault())
        )
        tintLocationPin(getColor(R.color.camera_accent))
        coordinatesText!!.text = GeoTextFormatter.formatCoordinates(
                currentLocation, Locale.getDefault())
        val altitude = if (currentLocation!!.hasAltitude())
            String.format(Locale.getDefault(), "ALT %.0f m", currentLocation!!.altitudeMeters())
        else
            "ALT —"
        val speed = if (java.lang.Double.isFinite(currentLocation!!.speedMetersPerSecond()))
            String.format(Locale.getDefault(), "SPD %.1f m/s", currentLocation!!.speedMetersPerSecond())
        else
            "SPD —"
        val satellites = if (currentLocation!!.satellites() > 0)
            "SAT " + currentLocation!!.satellites()
        else
            "SAT —"
        detailsText!!.text = getString(R.string.details_summary, altitude, speed, satellites)
    }

    private fun renderFlash() {
        if (flashButton == null) {
            return
        }
        val mode = if (cameraController == null)
            CameraController.FLASH_OFF
        else
            cameraController!!.flashMode()
        if (mode == CameraController.FLASH_AUTO) {
            applyIcon(flashButton, R.drawable.ic_flash_auto)
            flashButton!!.contentDescription = getString(R.string.flash_auto)
        } else if (mode == CameraController.FLASH_ON) {
            applyIcon(flashButton, R.drawable.ic_flash)
            flashButton!!.contentDescription = getString(R.string.flash_on)
        } else {
            applyIcon(flashButton, R.drawable.ic_flash_off)
            flashButton!!.contentDescription = getString(R.string.flash_off)
        }
        setIconAlpha(flashButton, if (flashButton!!.isEnabled) 1.0f else 0.38f)
    }

    private fun renderLens() {
        if (lensButton != null) {
            lensButton!!.contentDescription = getString(
                    if (cameraController != null && cameraController!!.isFrontFacing())
                        R.string.lens_front
                    else
                        R.string.lens_back
            )
        }
    }

    private fun renderZoom() {
        if (zoomText != null && cameraController != null) {
            zoomText!!.text = getString(R.string.zoom_compact, cameraController!!.zoomRatio())
        }
    }

    private fun updateCaptureControls() {
        if (captureButton == null) {
            return
        }
        val canCapture = cameraReady && !saving && hasRequiredStoragePermission()
        captureButton!!.isEnabled = canCapture
        captureButton!!.alpha = if (canCapture) 1.0f else 0.42f
        if (flashButton != null) {
            flashButton!!.visibility = if (cameraHasFlash) View.VISIBLE else View.GONE
            flashButton!!.isEnabled = cameraReady && cameraHasFlash && !saving
            setIconAlpha(flashButton, if (flashButton!!.isEnabled) 1.0f else 0.38f)
        }
        if (lensButton != null) {
            lensButton!!.visibility = if (cameraCanSwitchLens) View.VISIBLE else View.GONE
            lensButton!!.isEnabled = cameraReady && cameraCanSwitchLens && !saving
            setIconAlpha(lensButton, if (lensButton!!.isEnabled) 1.0f else 0.38f)
        }
    }

    private fun updatePermissionUi() {
        val missing = missingPermissions()
        val cameraMissing = !hasCameraPermission()
        val storageMissing = !hasRequiredStoragePermission()
        val blocked = cameraMissing || storageMissing
        if (permissionPanel != null) {
            permissionPanel!!.visibility = if (blocked) View.VISIBLE else View.GONE
        }
        if (topBarView != null) {
            topBarView!!.visibility = if (blocked) View.GONE else View.VISIBLE
        }
        if (zoomText != null) {
            zoomText!!.visibility = if (blocked) View.INVISIBLE else View.VISIBLE
        }
        if (!blocked) {
            showTopChrome()
        }
        if (permissionTitle != null) {
            permissionTitle!!.setText(if (storageMissing && !cameraMissing)
                R.string.permission_storage_title
            else
                R.string.permission_camera_title)
        }
        if (permissionMessage != null) {
            permissionMessage!!.setText(if (storageMissing && !cameraMissing)
                R.string.permission_storage_message
            else
                R.string.permission_camera_message)
        }
        renderPermissionAction()
        if (locationStatus != null && !hasLocationPermission()) {
            renderLocationStatus()
        }
        if (captureButton != null) {
            updateCaptureControls()
        }
    }

    private fun requestMissingPermissions() {
        val missing = missingPermissions()
        if (missing.isEmpty() || permissionRequestLaunched) {
            return
        }
        permissionRequestLaunched = true
        markPermissionsAsked()
        requestPermissions(missing.toTypedArray(), PERMISSION_REQUEST_CODE)
    }

    private fun onPermissionAction() {
        if (canAskForPermissions()) {
            requestMissingPermissions()
        } else {
            openAppSettings()
        }
    }

    private fun canAskForPermissions(): Boolean {
        if (!hasAskedForPermissions()) {
            return true
        }
        for (permission in missingPermissions()) {
            if (shouldShowRequestPermissionRationale(permission)) {
                return true
            }
        }
        return false
    }

    private fun canAskForLocation(): Boolean {
        if (!hasAskedForPermissions()) {
            return true
        }
        return shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                || shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun renderPermissionAction() {
        if (permissionButton == null) {
            return
        }
        val askAgain = canAskForPermissions()
        permissionButton!!.setText(if (askAgain)
            R.string.grant_access_label
        else
            R.string.open_settings_label)
        permissionButton!!.contentDescription = getString(if (askAgain)
            R.string.grant_access
        else
            R.string.open_settings)
    }

    private fun openAppSettings() {
        val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (exception: android.content.ActivityNotFoundException) {
            showStatusMessage(getString(R.string.settings_unavailable), getColor(R.color.camera_gold_bright))
        }
    }

    private fun hasAskedForPermissions(): Boolean {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREFS_PERMISSIONS_ASKED, false)
    }

    private fun markPermissionsAsked() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREFS_PERMISSIONS_ASKED, true)
                .apply()
    }


    private fun missingPermissions(): Set<String> {
        val missing = LinkedHashSet<String>()
        if (!hasCameraPermission()) {
            missing.add(Manifest.permission.CAMERA)
        }
        if (!hasLocationPermission()) {
            missing.add(Manifest.permission.ACCESS_FINE_LOCATION)
            missing.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (!hasRequiredStoragePermission()) {
            missing.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        return missing
    }

    private fun hasCameraPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasLocationPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun hasRequiredStoragePermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun configureWindow() {
        val window: Window = this.window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        } else {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = getColor(R.color.camera_navy_deep)
        }
        hideSystemUi()
    }

    private fun hideSystemUi() {
        val decor = window.decorView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = decor.windowInsetsController
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                return
            }
        }
        decor.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
    }

    private fun label(
            text: String,
            textSizeSp: Float,
            color: Int
    ): TextView {
        val view = TextView(this)
        view.text = text
        view.textSize = textSizeSp
        view.setTextColor(color)
        return view
    }

    private fun roundedBackground(
            color: Int,
            radius: Float
    ): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.setColor(color)
        drawable.cornerRadius = radius
        return drawable
    }

    private fun outlinedBackground(
            color: Int,
            strokeColor: Int,
            radius: Float,
            strokeWidth: Int
    ): GradientDrawable {
        val drawable = roundedBackground(color, radius)
        drawable.setStroke(strokeWidth, strokeColor)
        return drawable
    }

    private fun pillBackground(color: Int): GradientDrawable {
        return roundedBackground(color, dp(50.0f).toFloat())
    }

    private fun topSheetBackground(color: Int, radius: Float): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.setColor(color)
        drawable.setCornerRadii(floatArrayOf(
                radius, radius,
                radius, radius,
                0.0f, 0.0f,
                0.0f, 0.0f
        ))
        return drawable
    }


    private fun matchParent(): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(
                ViewGroupLayout.MATCH_PARENT,
                ViewGroupLayout.MATCH_PARENT
        )
    }

    private fun wrap(topMargin: Int): LinearLayout.LayoutParams {
        val params = LinearLayout.LayoutParams(
                ViewGroupLayout.WRAP_CONTENT,
                ViewGroupLayout.WRAP_CONTENT
        )
        params.topMargin = topMargin
        return params
    }

    private fun square(size: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(size, size)
    }

    private fun dp(value: Float): Int {
        return Math.round(value * resources.displayMetrics.density)
    }

    private object ViewGroupLayout {
        val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
        val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
