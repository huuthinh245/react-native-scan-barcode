package com.example.studyKotlin.view.ui.cameraview

import android.graphics.ImageFormat
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class ZXingBarcodeAnalyzer(
  private val onQRCodesDetected: (qrCodes: String) -> Unit,
        private val mWidth: Int,
        private val mHeight: Int,
        private var mRectOfInterest: Rect? = null
) : ImageAnalysis.Analyzer {

    private var multiFormatReader: MultiFormatReader = MultiFormatReader()
    private var isScanning = AtomicBoolean(false)

    override fun analyze(image: ImageProxy) {

        if (isScanning.get()) {
            image.close()
            return
        }

        isScanning.set(true)

        if ((image.format == ImageFormat.YUV_420_888 || image.format == ImageFormat.YUV_422_888
                        || image.format == ImageFormat.YUV_444_888) && image.planes.size == 3
        ) {
            val buffer: ByteBuffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val left:Int = mRectOfInterest?.left ?: 0
            val top:Int = mRectOfInterest?.top ?: 0
            val scanWidth:Int = mRectOfInterest?.width() ?: mWidth
            val scanHeight:Int = mRectOfInterest?.height() ?: mHeight
          Log.d("size","left: " + left + " top: " + top)
          Log.d("size","scanWidth: " + scanWidth + " scanHeight: " + scanHeight)
          Log.d("size","mwidth: " + mWidth + " mheight: " + mHeight)
            val rotatedImage = RotatedImage(bytes, image.width, image.height)
            rotateImageArray(rotatedImage, image.imageInfo.rotationDegrees)

            val bitmap = generateBitmapFromImageData(
                    rotatedImage.byteArray,
                    mWidth,
                    mHeight,
                    false,
                    left,
                    top,
                  scanWidth,
                    scanHeight
            )

            try {
                val rawResult = multiFormatReader.decodeWithState(bitmap)
                Log.d("Barcode:", rawResult.text)
              onQRCodesDetected(rawResult.text)
            } catch (e: NotFoundException) {
                e.printStackTrace()
            } finally {
                multiFormatReader.reset()
                image.close()
            }

            isScanning.set(false)
        }
    }

    // 90, 180. 270 rotation
    private fun rotateImageArray(imageToRotate: RotatedImage, rotationDegrees: Int) {
        if (rotationDegrees == 0) return // no rotation
        if (rotationDegrees % 90 != 0) return // only 90 degree times rotations

        val width = imageToRotate.width
        val height = imageToRotate.height
        Log.d("rotationDegrees", rotationDegrees.toString())
        val rotatedData = ByteArray(imageToRotate.byteArray.size)
        for (y in 0 until height) { // we scan the array by rows
            for (x in 0 until width) {
                when (rotationDegrees) {
                    90 -> rotatedData[x * height + height - y - 1] =
                            imageToRotate.byteArray[x + y * width] // Fill from top-right toward left (CW)
                    180 -> rotatedData[width * (height - y - 1) + width - x - 1] =
                            imageToRotate.byteArray[x + y * width] // Fill from bottom-right toward up (CW)
                    270 -> rotatedData[y + x * height] =
                            imageToRotate.byteArray[y * width + width - x - 1] // The opposite (CCW) of 90 degrees
                }
            }
        }

        imageToRotate.byteArray = rotatedData

        if (rotationDegrees != 180) {
            imageToRotate.height = width
            imageToRotate.width = height
        }
    }

    private class RotatedImage(var byteArray: ByteArray, var width: Int, var height: Int)
    private fun generateBitmapFromImageData(imageData: ByteArray, width: Int, height: Int, inverse: Boolean, left: Int, top: Int, sWidth: Int, sHeight: Int): BinaryBitmap {
        val source = PlanarYUVLuminanceSource(
                imageData,  // byte[] yuvData
                width,  // int dataWidth
                height,  // int dataHeight
                left,  // int left
                top,  // int top
                sWidth,  // int width
                sHeight,  // int height
                false // boolean reverseHorizontal
        )

        return if (inverse) {
            BinaryBitmap(HybridBinarizer(source.invert()))
        } else {
            BinaryBitmap(HybridBinarizer(source))
        }
    }
}
