package com.perfectgps.camera.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Size
import android.view.Surface
import android.view.TextureView
import com.perfectgps.camera.util.CameraOrientationCalculator
import com.perfectgps.camera.util.ZoomPolicy
import java.nio.ByteBuffer
import java.util.Collections
import java.util.Comparator

class CameraController(
        private val activity: Activity,
        private val previewView: TextureView,
        private val callback: Callback
) : TextureView.SurfaceTextureListener {
    companion object {
        const val FLASH_OFF = 0
        const val FLASH_AUTO = 1
        const val FLASH_ON = 2

        private const val MAX_PREVIEW_PIXELS = 13_000_000L
        private const val MAX_STILL_PIXELS = 16_000_000L
        private const val FOCUS_TIMEOUT_MILLIS = 1_200L
        private const val CAPTURE_TIMEOUT_MILLIS = 5_000L
        private const val FOCUS_TRIGGER_HOLD_MILLIS = 220L

        private fun clamp(value: Float, minimum: Float, maximum: Float): Float {
            return Math.max(minimum, Math.min(maximum, value))
        }

        private fun clamp(value: Int, minimum: Int, maximum: Int): Int {
            return Math.max(minimum, Math.min(maximum, value))
        }

        private fun pixelCount(size: Size): Long {
            return size.width.toLong() * size.height
        }
    }

    interface Callback {
        fun onCameraReady(
                frontFacing: Boolean,
                flashAvailable: Boolean,
                canSwitchLens: Boolean
        )

        fun onCameraError(message: String)
    }

    private val cameraManager: CameraManager? =
            activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager?
    private val mainHandler = Handler(android.os.Looper.getMainLooper())
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var previewSurface: Surface? = null
    private var previewSize: Size? = null
    private var backCameraId: String? = null
    private var frontCameraId: String? = null
    private var currentCameraId: String? = null
    private var sensorActiveArray: android.graphics.Rect? = null
    private var sensorOrientation = 0
    private var lensFacing = CameraCharacteristics.LENS_FACING_BACK
    private var flashMode = FLASH_OFF
    private var cameraGeneration = 0
    private var maxDigitalZoom = 1.0f
    private var zoomRatio = 1.0f
    @Volatile
    private var lastJpegOrientation = 0
    private var afMode = CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
    private var focusMode = CameraMetadata.CONTROL_AF_MODE_AUTO
    private var awbMode = CameraMetadata.CONTROL_AWB_MODE_AUTO
    private var previewNoiseReductionMode = -1
    private var stillNoiseReductionMode = -1
    private var previewAberrationMode = -1
    private var stillAberrationMode = -1
    private var previewShadingMode = -1
    private var stillShadingMode = -1
    private var previewEdgeMode = -1
    private var stillEdgeMode = -1
    private var stillTonemapMode = -1
    private var previewDistortionCorrectionMode = -1
    private var stillDistortionCorrectionMode = -1
    private var opticalStabilizationAvailable = false
    private var captureCycle = 0
    private var capturePhase = 0
    private var focusStartedAt = 0L
    private var precaptureStartedAt = 0L
    private var surfaceAvailable = false
    private var flashAvailable = false
    @Volatile
    private var ready = false
    @Volatile
    private var capturing = false

    init {
        previewView.setSurfaceTextureListener(this)
        previewView.addOnLayoutChangeListener({ view, left, top, right, bottom,
                                                oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                configureTransform()
            }
        })
        if (previewView.isAvailable) {
            val texture = previewView.surfaceTexture
            if (texture != null) {
                onSurfaceTextureAvailable(texture, previewView.width, previewView.height)
            }
        }
    }

    fun start() {
        if (cameraManager == null) {
            callback.onCameraError("Camera service is unavailable")
            return
        }
        if (!hasCameraPermission()) {
            return
        }
        ensureCameraThread()
        discoverCameraIds()
        if (surfaceAvailable) {
            postOpenCamera()
        }
    }

    fun pause() {
        ready = false
        cameraHandler?.post { closeCamera() }
    }

    fun close() {
        ready = false
        cameraHandler?.post {
            closeCamera()
            cameraThread!!.quitSafely()
            cameraThread = null
            cameraHandler = null
        }
    }

    fun capture() {
        if (!ready || capturing || captureSession == null) {
            return
        }
        capturing = true
        cameraHandler!!.post { captureStill() }
    }

    fun cycleFlash() {
        if (!flashAvailable) {
            return
        }
        flashMode = if (flashMode == FLASH_OFF) {
            FLASH_AUTO
        } else if (flashMode == FLASH_AUTO) {
            FLASH_ON
        } else {
            FLASH_OFF
        }
        refreshPreview()
    }

    fun flashMode(): Int {
        return flashMode
    }

    fun setFlashMode(mode: Int): Boolean {
        if (!flashAvailable) {
            return false
        }
        val resolved = if (mode == FLASH_ON) FLASH_ON else if (mode == FLASH_AUTO) FLASH_AUTO else FLASH_OFF
        if (flashMode == resolved) {
            return false
        }
        flashMode = resolved
        refreshPreview()
        return true
    }

    fun switchLens() {
        if (backCameraId == null || frontCameraId == null || capturing) {
            return
        }
        lensFacing = if (lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        zoomRatio = ZoomPolicy.afterLensSwitch()
        cameraHandler?.post {
            closeCamera()
            postOpenCamera()
        }
    }

    fun setZoom(requestedRatio: Float) {
        zoomRatio = ZoomPolicy.clamp(requestedRatio, maxDigitalZoom)
        if (ready && !capturing) {
            refreshPreview()
        }
    }

    fun zoomRatio(): Float {
        return zoomRatio
    }

    fun lastJpegOrientation(): Int {
        return lastJpegOrientation
    }

    fun focusAt(viewX: Float, viewY: Float, viewWidth: Int, viewHeight: Int) {
        val session: CameraCaptureSession? = captureSession
        val device: CameraDevice? = cameraDevice
        val surface: Surface? = previewSurface
        if (session == null || device == null || surface == null) {
            return
        }
        val activeArray: android.graphics.Rect? = sensorActiveArray
        val size: Size? = previewSize
        if (activeArray == null || size == null) {
            return
        }
        if (capturing || !ready) {
            return
        }
        val forward = previewTransformMatrix(viewWidth, viewHeight) ?: return
        val inverse = Matrix()
        if (!forward.invert(inverse)) {
            return
        }
        val point = floatArrayOf(viewX, viewY)
        inverse.mapPoints(point)
        val ratio = Math.max(1.0f, zoomRatio)
        val sensorWidth = Math.max(1, Math.round(activeArray.width().toFloat() / ratio))
        val sensorHeight = Math.max(1, Math.round(activeArray.height().toFloat() / ratio))
        val sensorLeft = activeArray.left + (activeArray.width() - sensorWidth) / 2
        val sensorTop = activeArray.top + (activeArray.height() - sensorHeight) / 2
        val clampedX = clamp(point[0], 0.0f, size.width.toFloat())
        val clampedY = clamp(point[1], 0.0f, size.height.toFloat())
        val centerX = sensorLeft + Math.round(clampedX / size.width.toFloat() * sensorWidth)
        val centerY = sensorTop + Math.round(clampedY / size.height.toFloat() * sensorHeight)
        val regionWidth = Math.min(sensorWidth, Math.max(1, Math.round(sensorWidth * 0.12f)))
        val regionHeight = Math.min(sensorHeight, Math.max(1, Math.round(sensorHeight * 0.12f)))
        val regionLeft = clamp(centerX - regionWidth / 2, sensorLeft, sensorLeft + sensorWidth - regionWidth)
        val regionTop = clamp(centerY - regionHeight / 2, sensorTop, sensorTop + sensorHeight - regionHeight)
        val region = android.graphics.Rect(
                regionLeft,
                regionTop,
                regionLeft + regionWidth,
                regionTop + regionHeight
        )
        try {
            val afRegions = arrayOf(
                    MeteringRectangle(region, MeteringRectangle.METERING_WEIGHT_MAX)
            )
            val builder = createBaseRequest(CameraDevice.TEMPLATE_PREVIEW, false)
            builder.set(CaptureRequest.CONTROL_AF_MODE, focusMode.toInt())
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, afRegions)
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, afRegions)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START.toInt())
            applyFlash(builder)
            applyZoom(builder)
            builder.addTarget(surface)
            session.capture(builder.build(), null, cameraHandler)
            cameraHandler!!.postDelayed({ restoreContinuousPreview() }, FOCUS_TRIGGER_HOLD_MILLIS)
        } catch (exception: CameraAccessException) {
            refreshPreview()
        } catch (exception: IllegalStateException) {
            refreshPreview()
        } catch (exception: IllegalArgumentException) {
            refreshPreview()
        }
    }

    private fun restoreContinuousPreview() {
        val session: CameraCaptureSession? = captureSession
        val device: CameraDevice? = cameraDevice
        val surface: Surface? = previewSurface
        if (session == null || device == null || surface == null) {
            return
        }
        if (capturing) {
            return
        }
        try {
            val builder = createBaseRequest(CameraDevice.TEMPLATE_PREVIEW, false)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE.toInt())
            applyFlash(builder)
            applyZoom(builder)
            builder.addTarget(surface)
            session.setRepeatingRequest(builder.build(), null, cameraHandler)
        } catch (exception: CameraAccessException) {
            ready = false
            mainHandler.post { callback.onCameraError("Camera preview stopped unexpectedly") }
        } catch (exception: IllegalStateException) {
            ready = false
            mainHandler.post { callback.onCameraError("Camera preview stopped unexpectedly") }
        } catch (exception: IllegalArgumentException) {
            ready = false
            mainHandler.post { callback.onCameraError("Camera preview stopped unexpectedly") }
        }
    }

    fun isFrontFacing(): Boolean {
        return lensFacing == CameraCharacteristics.LENS_FACING_FRONT
    }

    fun canSwitchLens(): Boolean {
        return backCameraId != null && frontCameraId != null
    }

    fun isFlashAvailable(): Boolean {
        return flashAvailable
    }

    fun isReady(): Boolean {
        return ready
    }

    override fun onSurfaceTextureAvailable(
            surface: SurfaceTexture,
            width: Int,
            height: Int
    ) {
        surfaceAvailable = true
        if (hasCameraPermission()) {
            ensureCameraThread()
            discoverCameraIds()
            postOpenCamera()
        }
    }

    override fun onSurfaceTextureSizeChanged(
            surface: SurfaceTexture,
            width: Int,
            height: Int
    ) {
        postConfigureTransform()
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        surfaceAvailable = false
        cameraHandler?.post { closeCamera() }
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
    }

    private fun ensureCameraThread() {
        if (cameraThread != null) {
            return
        }
        val thread = HandlerThread("PerfectGpsCamera")
        thread.start()
        cameraThread = thread
        cameraHandler = Handler(thread.looper)
    }

    private fun hasCameraPermission(): Boolean {
        return activity.checkSelfPermission(Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun discoverCameraIds() {
        val manager = cameraManager ?: return
        try {
            backCameraId = null
            frontCameraId = null
            var bestBackCharacteristics: CameraCharacteristics? = null
            var bestFrontCharacteristics: CameraCharacteristics? = null
            var fallbackId: String? = null
            val cameraIds = manager.cameraIdList
            for (id in cameraIds) {
                val characteristics = manager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    val best = bestFrontCharacteristics
                    if (best == null
                            || cameraQualityScore(characteristics) > cameraQualityScore(best)) {
                        bestFrontCharacteristics = characteristics
                        frontCameraId = id
                    }
                } else if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    val best = bestBackCharacteristics
                    if (best == null
                            || cameraQualityScore(characteristics) > cameraQualityScore(best)) {
                        bestBackCharacteristics = characteristics
                        backCameraId = id
                    }
                } else if (fallbackId == null) {
                    fallbackId = id
                }
            }
            if (backCameraId == null) {
                backCameraId = fallbackId
            }
            if (backCameraId == null && frontCameraId == null && cameraIds.size > 0) {
                backCameraId = cameraIds[0]
            }
            if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT
                    && frontCameraId == null
                    && backCameraId != null) {
                lensFacing = CameraCharacteristics.LENS_FACING_BACK
            }
        } catch (exception: CameraAccessException) {
            mainHandler.post { callback.onCameraError("Unable to inspect camera lenses") }
        }
    }

    private fun cameraQualityScore(characteristics: CameraCharacteristics): Long {
        val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
        var score = if (hasFlash == true) 100_000_000L else 0L
        val activeArray = characteristics.get(
                CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE
        )
        if (activeArray != null) {
            score += Math.min(activeArray.width().toLong() * activeArray.height(), 100_000_000L)
        }
        val map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        )
        if (map != null) {
            score += Math.min(maxOutputPixels(map.getOutputSizes(ImageFormat.JPEG)), 100_000_000L)
            score += Math.min(maxOutputPixels(map.getOutputSizes(SurfaceTexture::class.java)), 50_000_000L)
        }
        return score
    }

    private fun maxOutputPixels(sizes: Array<Size>?): Long {
        var maximum = 0L
        if (sizes == null) {
            return maximum
        }
        for (size in sizes) {
            maximum = Math.max(maximum, pixelCount(size))
        }
        return maximum
    }

    private fun postOpenCamera() {
        cameraHandler?.post { openCamera() }
    }

    private fun openCamera() {
        if (!surfaceAvailable
                || !hasCameraPermission()
                || cameraDevice != null
                || captureSession != null) {
            return
        }
        val requestedId = if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            frontCameraId
        } else {
            backCameraId
        }
        if (requestedId == null) {
            mainHandler.post { callback.onCameraError("No compatible camera was found") }
            return
        }
        closeCamera()
        val generation = ++cameraGeneration
        try {
            val characteristics = cameraManager!!.getCameraCharacteristics(requestedId)
            val orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
            val activeArray = characteristics.get(
                    CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE
            )
            val maxZoomValue = characteristics.get(
                    CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM
            )
            sensorOrientation = if (orientation == null) 0 else orientation
            lensFacing = if (facing == null) CameraCharacteristics.LENS_FACING_BACK else facing
            flashAvailable = hasFlash == true
            sensorActiveArray = activeArray
            maxDigitalZoom = if (maxZoomValue == null) 1.0f else Math.max(1.0f, maxZoomValue)
            zoomRatio = Math.max(1.0f, Math.min(zoomRatio, maxDigitalZoom))
            configureQualityModes(characteristics)
            configureStreams(requestedId, characteristics)
            cameraManager!!.openCamera(requestedId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (generation != cameraGeneration) {
                        camera.close()
                        return
                    }
                    cameraDevice = camera
                    currentCameraId = requestedId
                    createCaptureSession(generation)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    if (generation == cameraGeneration) {
                        closeCamera()
                        mainHandler.post { callback.onCameraError("Camera disconnected") }
                    }
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    if (generation == cameraGeneration) {
                        closeCamera()
                        mainHandler.post { callback.onCameraError("Camera could not be opened") }
                    }
                }
            }, cameraHandler)
        } catch (exception: CameraAccessException) {
            mainHandler.post { callback.onCameraError("Camera permission or access is unavailable") }
        } catch (exception: SecurityException) {
            mainHandler.post { callback.onCameraError("Camera permission or access is unavailable") }
        } catch (exception: IllegalArgumentException) {
            mainHandler.post { callback.onCameraError("Camera permission or access is unavailable") }
        }
    }

    private fun configureStreams(
            requestedId: String,
            characteristics: CameraCharacteristics
    ) {
        val map: StreamConfigurationMap? = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        )
        if (map == null) {
            throw CameraAccessException(CameraAccessException.CAMERA_ERROR,
                    "Camera stream configuration is unavailable")
        }
        previewSize = chooseBestSize(
                map.getOutputSizes(SurfaceTexture::class.java),
                MAX_PREVIEW_PIXELS
        )
        val jpegSize = chooseBestSize(
                map.getOutputSizes(ImageFormat.JPEG),
                MAX_STILL_PIXELS
        )
        val reader = ImageReader.newInstance(
                jpegSize.width,
                jpegSize.height,
                ImageFormat.JPEG,
                2
        )
        imageReader = reader
        reader.setOnImageAvailableListener({ source -> onImageAvailable(source) }, cameraHandler)
    }

    @Suppress("DEPRECATION")
    private fun createCaptureSession(generation: Int) {
        try {
            val texture: SurfaceTexture? = previewView.surfaceTexture
            val device: CameraDevice? = cameraDevice
            val reader: ImageReader? = imageReader
            val size: Size? = previewSize
            if (texture == null || device == null || reader == null || size == null) {
                return
            }
            texture.setDefaultBufferSize(size.width, size.height)
            val surface = Surface(texture)
            previewSurface = surface
            val surfaces = ArrayList<Surface>()
            surfaces.add(surface)
            surfaces.add(reader.surface)
            device.createCaptureSession(
                    surfaces,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            if (generation != cameraGeneration || cameraDevice == null) {
                                session.close()
                                return
                            }
                            captureSession = session
                            ready = true
                            startPreview()
                            mainHandler.post {
                                previewView.scaleX = if (isFrontFacing()) -1.0f else 1.0f
                                configureTransform()
                                callback.onCameraReady(
                                        isFrontFacing(),
                                        flashAvailable,
                                        canSwitchLens()
                                )
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            session.close()
                            if (generation == cameraGeneration) {
                                closeCamera()
                                mainHandler.post { callback.onCameraError("Camera preview configuration failed") }
                            }
                        }
                    },
                    cameraHandler
            )
        } catch (exception: CameraAccessException) {
            closeCamera()
            mainHandler.post { callback.onCameraError("Unable to configure camera preview") }
        } catch (exception: IllegalArgumentException) {
            closeCamera()
            mainHandler.post { callback.onCameraError("Unable to configure camera preview") }
        } catch (exception: IllegalStateException) {
            closeCamera()
            mainHandler.post { callback.onCameraError("Unable to configure camera preview") }
        }
    }

    private fun configureQualityModes(characteristics: CameraCharacteristics) {
        val afModes: IntArray? = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
        afMode = selectMode(
                afModes,
                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                CameraMetadata.CONTROL_AF_MODE_AUTO
        )
        if (afMode < 0) {
            afMode = CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        }
        focusMode = selectMode(
                afModes,
                CameraMetadata.CONTROL_AF_MODE_AUTO,
                afMode
        )
        if (focusMode < 0) {
            focusMode = afMode
        }

        val awbModes: IntArray? = characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
        awbMode = selectMode(awbModes, CameraMetadata.CONTROL_AWB_MODE_AUTO, -1)

        val noiseModes: IntArray? = characteristics.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
        previewNoiseReductionMode = selectMode(
                noiseModes,
                CaptureRequest.NOISE_REDUCTION_MODE_FAST,
                CaptureRequest.NOISE_REDUCTION_MODE_OFF
        )
        stillNoiseReductionMode = selectMode(
                noiseModes,
                CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY,
                CaptureRequest.NOISE_REDUCTION_MODE_OFF
        )

        val aberrationModes: IntArray? = characteristics.get(
                CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES
        )
        previewAberrationMode = selectMode(
                aberrationModes,
                CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_FAST,
                CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF
        )
        stillAberrationMode = selectMode(
                aberrationModes,
                CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY,
                CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF
        )

        val shadingModes: IntArray? = characteristics.get(CameraCharacteristics.SHADING_AVAILABLE_MODES)
        previewShadingMode = selectMode(
                shadingModes,
                CaptureRequest.SHADING_MODE_FAST,
                CaptureRequest.SHADING_MODE_OFF
        )
        stillShadingMode = selectMode(
                shadingModes,
                CaptureRequest.SHADING_MODE_HIGH_QUALITY,
                CaptureRequest.SHADING_MODE_OFF
        )

        val edgeModes: IntArray? = characteristics.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)
        previewEdgeMode = selectMode(
                edgeModes,
                CaptureRequest.EDGE_MODE_FAST,
                CaptureRequest.EDGE_MODE_OFF
        )
        stillEdgeMode = selectMode(
                edgeModes,
                CaptureRequest.EDGE_MODE_HIGH_QUALITY,
                CaptureRequest.EDGE_MODE_OFF
        )

        val tonemapModes: IntArray? = characteristics.get(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES)
        stillTonemapMode = selectMode(
                tonemapModes,
                CameraMetadata.TONEMAP_MODE_HIGH_QUALITY,
                CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE
        )

        var distortionModes: IntArray? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            distortionModes = characteristics.get(
                    CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES
            )
        }
        previewDistortionCorrectionMode = selectMode(
                distortionModes,
                CaptureRequest.DISTORTION_CORRECTION_MODE_FAST,
                CaptureRequest.DISTORTION_CORRECTION_MODE_OFF
        )
        stillDistortionCorrectionMode = selectMode(
                distortionModes,
                CaptureRequest.DISTORTION_CORRECTION_MODE_HIGH_QUALITY,
                CaptureRequest.DISTORTION_CORRECTION_MODE_OFF
        )

        // Preserved verbatim from Java: the key holds an int[] of supported modes, so the
        // Boolean.TRUE comparison below can never be true; do not "fix" it here.
        opticalStabilizationAvailable = (characteristics.get(
                CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION
        ) as Any?) == true
    }

    private fun selectMode(modes: IntArray?, preferred: Int, fallback: Int): Int {
        if (modes == null || modes.size == 0) {
            return -1
        }
        for (mode in modes) {
            if (mode == preferred) {
                return preferred
            }
        }
        if (fallback >= 0) {
            for (mode in modes) {
                if (mode == fallback) {
                    return fallback
                }
            }
        }
        return modes[0]
    }

    private fun chooseBestSize(choices: Array<Size>?, maximumPixels: Long): Size {
        if (choices == null || choices.size == 0) {
            throw IllegalArgumentException("No camera output size is available")
        }
        val ordered = orderedSizes(choices)
        for (size in ordered) {
            if (pixelCount(size) <= maximumPixels && isFourByThree(size)) {
                return size
            }
        }
        for (size in ordered) {
            if (pixelCount(size) <= maximumPixels) {
                return size
            }
        }
        return ordered[ordered.size - 1]
    }

    private fun isFourByThree(size: Size): Boolean {
        val longEdge = Math.max(size.width, size.height).toLong()
        val shortEdge = Math.min(size.width, size.height).toLong()
        return Math.abs(longEdge * 3L - shortEdge * 4L) * 100L <= longEdge * 2L
    }

    private fun orderedSizes(choices: Array<Size>): List<Size> {
        val ordered = ArrayList<Size>(choices.size)
        for (index in choices.indices) {
            ordered.add(choices[index])
        }
        Collections.sort(ordered, Comparator<Size> { first, second ->
            pixelCount(second).compareTo(pixelCount(first))
        })
        return ordered
    }

    private fun createBaseRequest(template: Int, still: Boolean): CaptureRequest.Builder {
        val builder = cameraDevice!!.createCaptureRequest(template)
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO.toInt())
        if (afMode >= 0) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, afMode.toInt())
        }
        if (awbMode >= 0) {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, awbMode.toInt())
        }
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON.toInt())
        applyQualitySettings(builder, still)
        return builder
    }

    private fun applyQualitySettings(builder: CaptureRequest.Builder, still: Boolean) {
        builder.set(CaptureRequest.JPEG_QUALITY, 100.toByte())
        if (still) {
            builder.set(CaptureRequest.JPEG_THUMBNAIL_QUALITY, 100.toByte())
        }
        val noiseReduction = if (still) stillNoiseReductionMode else previewNoiseReductionMode
        if (noiseReduction >= 0) {
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, noiseReduction.toInt())
        }
        val aberration = if (still) stillAberrationMode else previewAberrationMode
        if (aberration >= 0) {
            builder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, aberration.toInt())
        }
        val shading = if (still) stillShadingMode else previewShadingMode
        if (shading >= 0) {
            builder.set(CaptureRequest.SHADING_MODE, shading.toInt())
        }
        val edge = if (still) stillEdgeMode else previewEdgeMode
        if (edge >= 0) {
            builder.set(CaptureRequest.EDGE_MODE, edge.toInt())
        }
        val tonemap = if (still) stillTonemapMode else -1
        if (tonemap >= 0) {
            builder.set(CaptureRequest.TONEMAP_MODE, tonemap.toInt())
        }
        val distortion = if (still) stillDistortionCorrectionMode else previewDistortionCorrectionMode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && distortion >= 0) {
            builder.set(CaptureRequest.DISTORTION_CORRECTION_MODE, distortion.toInt())
        }
        if (opticalStabilizationAvailable) {
            builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON.toInt())
        }
    }

    private fun startPreview() {
        val session: CameraCaptureSession? = captureSession
        val device: CameraDevice? = cameraDevice
        val surface: Surface? = previewSurface
        if (session == null || device == null || surface == null) {
            return
        }
        try {
            val builder = createBaseRequest(CameraDevice.TEMPLATE_PREVIEW, false)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE.toInt())
            applyFlash(builder)
            applyZoom(builder)
            builder.addTarget(surface)
            session.setRepeatingRequest(builder.build(), null, cameraHandler)
        } catch (exception: CameraAccessException) {
            ready = false
            mainHandler.post { callback.onCameraError("Camera preview stopped unexpectedly") }
        } catch (exception: IllegalStateException) {
            ready = false
            mainHandler.post { callback.onCameraError("Camera preview stopped unexpectedly") }
        } catch (exception: IllegalArgumentException) {
            ready = false
            mainHandler.post { callback.onCameraError("Camera preview stopped unexpectedly") }
        }
    }

    private fun refreshPreview() {
        val handler = cameraHandler ?: return
        if (ready && !capturing) {
            handler.post { startPreview() }
        }
    }

    private fun captureStill() {
        val session: CameraCaptureSession? = captureSession
        val device: CameraDevice? = cameraDevice
        val surface: Surface? = previewSurface
        val reader: ImageReader? = imageReader
        if (session == null || device == null || surface == null || reader == null) {
            capturing = false
            return
        }
        val cycle = ++captureCycle
        capturePhase = 0
        focusStartedAt = SystemClock.elapsedRealtime()
        try {
            session.stopRepeating()
            submitFocusRequest(cycle)
            cameraHandler!!.postDelayed(
                    { captureTimeout(cycle) },
                    FOCUS_TIMEOUT_MILLIS
            )
        } catch (exception: CameraAccessException) {
            failCapture(cycle, "Photo capture could not start")
        } catch (exception: IllegalStateException) {
            failCapture(cycle, "Photo capture could not start")
        } catch (exception: IllegalArgumentException) {
            failCapture(cycle, "Photo capture could not start")
        }
    }

    private fun submitFocusRequest(cycle: Int) {
        if (!isCaptureActive(cycle) || capturePhase != 0) {
            return
        }
        try {
            val builder = createBaseRequest(CameraDevice.TEMPLATE_STILL_CAPTURE, false)
            if (focusMode >= 0) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, focusMode.toInt())
            }
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START.toInt())
            applyFlash(builder)
            applyZoom(builder)
            builder.addTarget(requireNotNull(previewSurface))
            captureSession!!.capture(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureFailed(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        failure: CaptureFailure
                ) {
                    failCapture(cycle, "Photo focus could not start")
                }

                override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                ) {
                    if (!isCaptureActive(cycle) || capturePhase != 0) {
                        return
                    }
                    val afState = result.get(CaptureResult.CONTROL_AF_STATE)
                    if (isFocusLocked(afState) || SystemClock.elapsedRealtime() - focusStartedAt >= FOCUS_TIMEOUT_MILLIS) {
                        submitPrecapture(cycle)
                    } else {
                        cameraHandler!!.postDelayed({ submitFocusRequest(cycle) }, 80L)
                    }
                }
            }, cameraHandler)
        } catch (exception: CameraAccessException) {
            failCapture(cycle, "Photo focus could not start")
        } catch (exception: IllegalStateException) {
            failCapture(cycle, "Photo focus could not start")
        } catch (exception: IllegalArgumentException) {
            failCapture(cycle, "Photo focus could not start")
        }
    }

    private fun submitPrecapture(cycle: Int) {
        if (!isCaptureActive(cycle) || capturePhase != 0) {
            return
        }
        capturePhase = 1
        precaptureStartedAt = SystemClock.elapsedRealtime()
        sendPrecapture(cycle)
    }

    private fun sendPrecapture(cycle: Int) {
        if (!isCaptureActive(cycle) || capturePhase != 1) {
            return
        }
        try {
            val builder = createBaseRequest(CameraDevice.TEMPLATE_STILL_CAPTURE, false)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE.toInt())
            builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START.toInt())
            applyFlash(builder)
            applyZoom(builder)
            builder.addTarget(requireNotNull(previewSurface))
            captureSession!!.capture(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureFailed(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        failure: CaptureFailure
                ) {
                    submitStill(cycle)
                }

                override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                ) {
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    val converged = aeState == null ||
                            aeState == CameraMetadata.CONTROL_AE_STATE_CONVERGED ||
                            aeState == CameraMetadata.CONTROL_AE_STATE_LOCKED ||
                            aeState == CameraMetadata.CONTROL_AE_STATE_PRECAPTURE
                    if (converged || SystemClock.elapsedRealtime() - precaptureStartedAt >= FOCUS_TIMEOUT_MILLIS) {
                        submitStill(cycle)
                    } else {
                        cameraHandler!!.postDelayed({ sendPrecapture(cycle) }, 50L)
                    }
                }
            }, cameraHandler)
        } catch (exception: CameraAccessException) {
            submitStill(cycle)
        } catch (exception: IllegalStateException) {
            submitStill(cycle)
        } catch (exception: IllegalArgumentException) {
            submitStill(cycle)
        }
    }

    private fun submitStill(cycle: Int) {
        if (!isCaptureActive(cycle) || capturePhase != 1) {
            return
        }
        capturePhase = 2
        lastJpegOrientation = CameraOrientationCalculator.jpegOrientation(
                sensorOrientation,
                displayRotationDegrees(),
                isFrontFacing()
        )
        try {
            val builder = createBaseRequest(CameraDevice.TEMPLATE_STILL_CAPTURE, true)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE.toInt())
            builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE.toInt())
            builder.set(
                    CaptureRequest.JPEG_ORIENTATION,
                    lastJpegOrientation.toInt()
            )
            applyFlash(builder)
            applyZoom(builder)
            builder.addTarget(imageReader!!.surface)
            captureSession!!.capture(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureFailed(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        failure: CaptureFailure
                ) {
                    failCapture(cycle, "Photo capture failed")
                }

                override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                ) {
                    startPreview()
                }
            }, cameraHandler)
            cameraHandler!!.postDelayed(
                    { captureTimeout(cycle) },
                    CAPTURE_TIMEOUT_MILLIS
            )
        } catch (exception: CameraAccessException) {
            failCapture(cycle, "Photo capture could not start")
        } catch (exception: IllegalStateException) {
            failCapture(cycle, "Photo capture could not start")
        } catch (exception: IllegalArgumentException) {
            failCapture(cycle, "Photo capture could not start")
        }
    }

    private fun captureTimeout(cycle: Int) {
        if (!isCaptureActive(cycle)) {
            return
        }
        if (capturePhase == 0) {
            submitPrecapture(cycle)
        } else if (capturePhase == 1) {
            submitStill(cycle)
        } else {
            failCapture(cycle, "Photo capture timed out")
        }
    }

    private fun isCaptureActive(cycle: Int): Boolean {
        return capturing && cycle == captureCycle && captureSession != null && imageReader != null
    }

    private fun isFocusLocked(afState: Int?): Boolean {
        return afState != null && (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED)
    }

    private fun failCapture(cycle: Int, message: String) {
        if (!isCaptureActive(cycle)) {
            return
        }
        capturePhase = 0
        capturing = false
        startPreview()
        mainHandler.post { callback.onCameraError(message) }
    }

    private fun onImageAvailable(reader: ImageReader) {
        try {
            val image: Image? = reader.acquireNextImage()
            if (image == null) {
                return
            }
            try {
                val buffer = image.planes[0].buffer
                val jpeg = ByteArray(buffer.remaining())
                buffer.get(jpeg)
                mainHandler.post {
                    capturing = false
                    capturePhase = 0
                    jpegConsumer?.onJpegCaptured(jpeg)
                }
            } finally {
                image.close()
            }
        } catch (exception: Exception) {
            capturing = false
            mainHandler.post { callback.onCameraError("Photo data could not be read") }
        }
    }

    private var jpegConsumer: JpegConsumer? = null

    fun setJpegConsumer(consumer: JpegConsumer?) {
        jpegConsumer = consumer
    }

    interface JpegConsumer {
        fun onJpegCaptured(jpeg: ByteArray)
    }

    private fun applyFlash(builder: CaptureRequest.Builder) {
        if (!flashAvailable) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON.toInt())
            return
        }
        if (flashMode == FLASH_AUTO) {
            builder.set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH.toInt()
            )
        } else if (flashMode == FLASH_ON) {
            builder.set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH.toInt()
            )
            builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_SINGLE.toInt())
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON.toInt())
            builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF.toInt())
        }
    }

    private fun applyZoom(builder: CaptureRequest.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio.toFloat())
        } else {
            val activeArray = sensorActiveArray
            if (activeArray != null) {
                val width = activeArray.width()
                val height = activeArray.height()
                val cropWidth = Math.max(1, Math.round(width.toFloat() / zoomRatio))
                val cropHeight = Math.max(1, Math.round(height.toFloat() / zoomRatio))
                val left = activeArray.left + (width - cropWidth) / 2
                val top = activeArray.top + (height - cropHeight) / 2
                val crop = android.graphics.Rect(
                        left,
                        top,
                        left + cropWidth,
                        top + cropHeight
                )
                builder.set(CaptureRequest.SCALER_CROP_REGION, crop)
            }
        }
    }

    private fun chooseSize(choices: Array<Size>?, maximumPixels: Long): Size {
        if (choices == null || choices.size == 0) {
            throw IllegalArgumentException("No camera output size is available")
        }
        val ordered = orderedSizes(choices)
        for (size in ordered) {
            if (pixelCount(size) <= maximumPixels) {
                return size
            }
        }
        return ordered[ordered.size - 1]
    }

    @Suppress("DEPRECATION")
    private fun displayRotationDegrees(): Int {
        val rotation = activity.windowManager.defaultDisplay.rotation
        return when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    private fun postConfigureTransform() {
        mainHandler.post { configureTransform() }
    }

    private fun configureTransform() {
        val matrix = previewTransformMatrix(previewView.width, previewView.height)
        if (matrix != null) {
            previewView.setTransform(matrix)
        }
    }

    private fun previewTransformMatrix(width: Int, height: Int): Matrix? {
        val size: Size? = previewSize
        if (width == 0 || height == 0 || size == null) {
            return null
        }
        val rotation = displayRotationDegrees()
        val relativeRotation = CameraOrientationCalculator.relativeRotation(
                sensorOrientation,
                rotation,
                isFrontFacing()
        )
        val rotationRequired = relativeRotation % 180 != 0
        val scaleX: Float
        val scaleY: Float
        if (sensorOrientation == 0) {
            scaleX = if (rotationRequired) width.toFloat() / size.width else width.toFloat() / size.height
            scaleY = if (rotationRequired) height.toFloat() / size.height else height.toFloat() / size.width
        } else {
            scaleX = if (rotationRequired) width.toFloat() / size.height else width.toFloat() / size.width
            scaleY = if (rotationRequired) height.toFloat() / size.width else height.toFloat() / size.height
        }
        val finalScale = Math.max(scaleX, scaleY)
        val centerX = width / 2.0f
        val centerY = height / 2.0f
        val matrix = Matrix()
        if (rotationRequired) {
            matrix.setScale(
                    finalScale / scaleX,
                    finalScale / scaleY,
                    centerX,
                    centerY
            )
        } else {
            matrix.setScale(
                    height.toFloat() / width / scaleY * finalScale,
                    width.toFloat() / height / scaleX * finalScale,
                    centerX,
                    centerY
            )
        }
        matrix.postRotate(-rotation.toFloat(), centerX, centerY)
        return matrix
    }

    private fun closeCamera() {
        cameraGeneration++
        ready = false
        capturing = false
        if (captureSession != null) {
            captureSession!!.close()
            captureSession = null
        }
        if (cameraDevice != null) {
            cameraDevice!!.close()
            cameraDevice = null
        }
        if (imageReader != null) {
            imageReader!!.close()
            imageReader = null
        }
        if (previewSurface != null) {
            previewSurface!!.release()
            previewSurface = null
        }
        currentCameraId = null
    }
}
