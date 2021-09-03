package com.reactnativescanner


import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.SensorManager
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleObserver
import com.example.studyKotlin.view.ui.cameraview.ZXingBarcodeAnalyzer
import com.facebook.react.uimanager.ThemedReactContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class RNCamera(context: ThemedReactContext) : FrameLayout(context), LifecycleObserver {
  private val currentContext: ThemedReactContext = context
  private var camera: Camera? = null
  private var preview: Preview? = null
  private var imageCapture: ImageCapture? = null
  private var imageAnalyzer: ImageAnalysis? = null
  private var orientationListener: OrientationEventListener? = null
  private var viewFinder: PreviewView = PreviewView(context)
  private var barcodeFrame: BarcodeFrame? = null
  private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
  private var cameraProvider: ProcessCameraProvider? = null
  private var outputPath: String? = null
  private var shutterAnimationDuration: Int = 50
  private var effectLayer = View(context)
  // Camera Props
  private var lensType = CameraSelector.LENS_FACING_BACK
  private var autoFocus = "on"
  private var zoomMode = "on"

  // Barcode Props
  private var scanBarcode: Boolean = false
  private var frameColor = Color.GREEN
  private var laserColor = Color.RED

  init {
    viewFinder.layoutParams = LinearLayout.LayoutParams(
      LayoutParams.MATCH_PARENT,
      LayoutParams.MATCH_PARENT
    )
    installHierarchyFitter(viewFinder)
    addView(viewFinder)

    effectLayer.alpha = 0F
    effectLayer.setBackgroundColor(Color.BLACK)
    addView(effectLayer)
  }

  @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (allPermissionsGranted()) {
      viewFinder.post {
        setupCamera()
      }
    }
  }

  fun setShowFrame(enabled: Boolean) {
    if (enabled) {
      barcodeFrame = BarcodeFrame(context)
      val actualPreviewWidth = resources.displayMetrics.widthPixels
      val actualPreviewHeight = resources.displayMetrics.heightPixels
      val height: Int = convertDeviceHeightToSupportedAspectRatio(actualPreviewWidth, actualPreviewHeight)
      barcodeFrame!!.setFrameColor(frameColor)
      barcodeFrame!!.setLaserColor(laserColor)
      (barcodeFrame as View).layout(0, 0, actualPreviewWidth, height)
      addView(barcodeFrame)
    } else if (barcodeFrame != null) {
      removeView(barcodeFrame)
      barcodeFrame = null
    }
  }

  fun setLaserColor(@ColorInt color: Int) {
    laserColor = color
    if (barcodeFrame != null) {
      barcodeFrame!!.setLaserColor(laserColor)
    }
  }

  fun setFrameColor(@ColorInt color: Int) {
    frameColor = color
    if (barcodeFrame != null) {
      barcodeFrame!!.setFrameColor(color)
    }
  }

  private fun convertDeviceHeightToSupportedAspectRatio(actualWidth: Int, actualHeight: Int): Int {
    val maxScreenRatio = 16 / 9f
    return (if (actualHeight / actualWidth > maxScreenRatio) actualWidth * maxScreenRatio else actualHeight).toString().toInt()
  }


  private fun installHierarchyFitter(view: ViewGroup) {
//    Log.d(TAG, "CameraView looking for ThemedReactContext")
    if (context is ThemedReactContext) { // only react-native setup
//      Log.d(TAG, "CameraView found ThemedReactContext")
      view.setOnHierarchyChangeListener(object : OnHierarchyChangeListener {
        override fun onChildViewRemoved(parent: View?, child: View?) = Unit
        override fun onChildViewAdded(parent: View?, child: View?) {
          parent?.measure(
            MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
          )
          parent?.layout(0, 0, parent.measuredWidth, parent.measuredHeight)
        }
      })
    }
  }

  @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
  private fun setupCamera() {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener(Runnable {
      // Used to bind the lifecycle of cameras to the lifecycle owner
      cameraProvider = cameraProviderFuture.get()

      // Rotate the image according to device orientation, even when UI orientation is locked
      orientationListener = object : OrientationEventListener(context, SensorManager.SENSOR_DELAY_UI) {
        override fun onOrientationChanged(orientation: Int) {
          val imageCapture = imageCapture ?: return
          var newOrientation: Int = imageCapture.targetRotation
          if (orientation >= 315 || orientation < 45) {
            newOrientation = Surface.ROTATION_0
          } else if (orientation in 225..314) {
            newOrientation = Surface.ROTATION_90
          } else if (orientation in 135..224) {
            newOrientation = Surface.ROTATION_180
          } else if (orientation in 45..134) {
            newOrientation = Surface.ROTATION_270
          }
          if (newOrientation != imageCapture.targetRotation) {
            imageCapture.targetRotation = newOrientation
//            onOrientationChange(newOrientation)
          }
        }
      }
      orientationListener!!.enable()

      val scaleDetector =  ScaleGestureDetector(context, object: ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector?): Boolean {
          if (zoomMode == "off") return true
          val cameraControl = camera?.cameraControl ?: return true
          val zoom = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: return true
          val scaleFactor = detector?.scaleFactor ?: return true
          val scale = zoom * scaleFactor
          cameraControl.setZoomRatio(scale)
          return true
        }
      })

      // Tap to focus
      viewFinder.setOnTouchListener { _, event ->
        if (event.action != MotionEvent.ACTION_UP) {
          return@setOnTouchListener scaleDetector.onTouchEvent(event)
        }
        focusOnPoint(event.x, event.y)
        return@setOnTouchListener true
      }

      bindCameraUseCases()
    }, ContextCompat.getMainExecutor(getActivity()))
  }

  @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
  private fun bindCameraUseCases() {
    if (viewFinder.display == null) return
    // Get screen metrics used to setup camera for full screen resolution
    val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
    Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

    val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
    Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")

    val rotation = viewFinder.display.rotation

    // CameraProvider
    val cameraProvider = cameraProvider
      ?: throw IllegalStateException("Camera initialization failed.")

    // CameraSelector
    val cameraSelector = CameraSelector.Builder().requireLensFacing(lensType).build()

    // Preview
    preview = Preview.Builder()
      // We request aspect ratio but no resolution
      .setTargetAspectRatio(screenAspectRatio)
      // Set initial target rotation
      .setTargetRotation(rotation)
      .build()

    // ImageCapture
    imageCapture = ImageCapture.Builder()
      .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
      // We request aspect ratio but no resolution to match preview config, but letting
      // CameraX optimize for whatever specific resolution best fits our use cases
      .setTargetAspectRatio(screenAspectRatio)
      // Set initial target rotation, we will have to call this again if rotation changes
      // during the lifecycle of this use case
      .setTargetRotation(rotation)
      .build()

    // ImageAnalysis
    imageAnalyzer = ImageAnalysis.Builder()
      .setTargetResolution(Size(viewFinder.width,viewFinder.height))
      .build()

    val useCases = mutableListOf(preview, imageCapture)

    if (scanBarcode) {
      val analyzer = ZXingBarcodeAnalyzer ({ barcodes ->
        if (barcodes.isNotEmpty()) {
          Log.d("barcode",barcodes)
//          onBarcodeRead(barcodes)
        }
      },viewFinder.width,viewFinder.height, barcodeFrame?.frameRect)
      imageAnalyzer!!.setAnalyzer(cameraExecutor, analyzer)
      useCases.add(imageAnalyzer)
    }

    // Must unbind the use-cases before rebinding them
    cameraProvider.unbindAll()

    try {
      // A variable number of use-cases can be passed here -
      // camera provides access to CameraControl & CameraInfo
      camera = cameraProvider.bindToLifecycle(getActivity() as AppCompatActivity, cameraSelector, *useCases.toTypedArray())

      // Attach the viewfinder's surface provider to preview use case
      preview?.setSurfaceProvider(viewFinder.surfaceProvider)
    } catch (exc: Exception) {
      Log.e(TAG, "Use case binding failed", exc)
    }
  }

  private fun getActivity() : Activity {
    return currentContext.currentActivity!!
  }

//  private fun onOrientationChange(orientation: Int) {
//    val remappedOrientation = when (orientation) {
//      Surface.ROTATION_0 -> RNCameraKitModule.PORTRAIT
//      Surface.ROTATION_90 -> RNCameraKitModule.LANDSCAPE_LEFT
//      Surface.ROTATION_180 -> RNCameraKitModule.PORTRAIT_UPSIDE_DOWN
//      Surface.ROTATION_270 -> RNCameraKitModule.LANDSCAPE_RIGHT
//      else -> {
//        Log.e(TAG, "CameraView: Unknown device orientation detected: $orientation")
//        return
//      }
//    }
//
//    val event: WritableMap = Arguments.createMap()
//    event.putInt("orientation", remappedOrientation)
//    currentContext.getJSModule(RCTEventEmitter::class.java).receiveEvent(
//      id,
//      "onOrientationChange",
//      event
//    )
//  }

  private fun focusOnPoint(x: Float?, y: Float?) {
    if (x === null || y === null) {
      camera?.cameraControl?.cancelFocusAndMetering()
      return
    }
    val factory = viewFinder.meteringPointFactory
    val builder = FocusMeteringAction.Builder(factory.createPoint(x, y))

    // Auto-cancel will clear focus points (and engage AF) after a duration
    if (autoFocus == "off") builder.disableAutoCancel()

    camera?.cameraControl?.startFocusAndMetering(builder.build())
  }

  private fun aspectRatio(width: Int, height: Int): Int {
    val previewRatio = max(width, height).toDouble() / min(width, height)
    if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
      return AspectRatio.RATIO_4_3
    }
    return AspectRatio.RATIO_16_9
  }

  private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
    ContextCompat.checkSelfPermission(
      context, it) == PackageManager.PERMISSION_GRANTED
  }

  fun setScanBarcode(enabled: Boolean) {
    val restartCamera = enabled != scanBarcode
    scanBarcode = enabled
    if (restartCamera) bindCameraUseCases()
  }

  companion object {

    private const val TAG = "CameraKit"
    private const val RATIO_4_3_VALUE = 4.0 / 3.0
    private const val RATIO_16_9_VALUE = 16.0 / 9.0
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
  }
}
