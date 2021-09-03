package com.reactnativescanner

import android.graphics.Color
import android.view.View
import androidx.annotation.ColorInt
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp

class ScannerViewManager : SimpleViewManager<RNCamera>() {
  override fun getName() = "ScannerView"

  override fun createViewInstance(reactContext: ThemedReactContext): RNCamera {
    return RNCamera(reactContext)
  }

  @ReactProp(name = "color")
  fun setColor(view: View, color: String) {
    view.setBackgroundColor(Color.parseColor(color))
  }
  @ReactProp(name = "cameraType")
  fun setCameraType(view: RNCamera, type: String) {
//    view.setCameraType(type)
  }

  @ReactProp(name = "flashMode")
  fun setFlashMode(view: RNCamera, mode: String?) {
//    view.setFlashMode(mode)
  }

  @ReactProp(name = "torchMode")
  fun setTorchMode(view: RNCamera, mode: String?) {
//    view.setTorchMode(mode)
  }

  @ReactProp(name = "focusMode")
  fun setFocusMode(view: RNCamera, mode: String) {
//    view.setAutoFocus(mode)
  }

  @ReactProp(name = "zoomMode")
  fun setZoomMode(view: RNCamera, mode: String) {
//    view.setZoomMode(mode)
  }

  @ReactProp(name = "scanBarcode")
  fun setScanBarcode(view: RNCamera, enabled: Boolean) {
    view.setScanBarcode(enabled)
  }

  @ReactProp(name = "showFrame")
  fun setShowFrame(view: RNCamera, enabled: Boolean) {
    view.setShowFrame(enabled)
  }

  @ReactProp(name = "laserColor", defaultInt = Color.RED)
  fun setLaserColor(view: RNCamera, @ColorInt color: Int) {
    view.setLaserColor(color)
  }

  @ReactProp(name = "frameColor", defaultInt = Color.GREEN)
  fun setFrameColor(view: RNCamera, @ColorInt color: Int) {
    view.setFrameColor(color)
  }

  @ReactProp(name = "outputPath")
  fun setOutputPath(view: RNCamera, path: String) {
//    view.setOutputPath(path)
  }

  @ReactProp(name = "shutterAnimationDuration")
  fun setShutterAnimationDuration(view: RNCamera, duration: Int) {
//    view.setShutterAnimationDuration(duration)
  }
}
