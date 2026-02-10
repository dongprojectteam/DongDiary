package com.doptsw.dongdiary.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import android.util.Base64
import java.io.File
import java.io.FileOutputStream

object ImageUtil {

    /**
     * Compress and convert image to Base64 string
     * Target size: 100-150KB
     * @param context Android context
     * @param imageUri URI of the image to compress
     * @return Base64 encoded string of compressed image, or null if failed
     */
    suspend fun compressImageToBase64(context: Context, imageUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            // Load image from URI
            val bitmap = loadBitmap(context, imageUri) ?: return@withContext null

            // Resize image if needed
            val resized = resizeBitmap(bitmap, maxWidth = 600, maxHeight = 600)

            // Compress to JPEG with quality optimization
            val compressed = compressToByteArray(resized)

            // Convert to Base64
            Base64.encodeToString(compressed, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Load bitmap from URI
     */
    private fun loadBitmap(context: Context, imageUri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                // read EXIF and rotate if needed
                try {
                    val exif = ExifInterface(ByteArrayInputStream(bytes))
                    val orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_UNDEFINED
                    )
                    return rotateBitmapIfRequired(bitmap, orientation)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    return bitmap
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun rotateBitmapIfRequired(bitmap: Bitmap?, orientation: Int): Bitmap? {
        if (bitmap == null) return null

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipBitmap(bitmap, horizontal = true, vertical = false)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipBitmap(bitmap, horizontal = false, vertical = true)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                // flip horizontal and rotate 270
                val flipped = flipBitmap(bitmap, horizontal = true, vertical = false)
                rotateBitmap(flipped ?: bitmap, 270f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                // flip horizontal and rotate 90
                val flipped = flipBitmap(bitmap, horizontal = true, vertical = false)
                rotateBitmap(flipped ?: bitmap, 90f)
            }
            else -> bitmap
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun flipBitmap(bitmap: Bitmap, horizontal: Boolean, vertical: Boolean): Bitmap? {
        val matrix = Matrix().apply { preScale(if (horizontal) -1f else 1f, if (vertical) -1f else 1f) }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Resize bitmap while maintaining aspect ratio
     */
    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        if (bitmap.width <= maxWidth && bitmap.height <= maxHeight) {
            return bitmap
        }

        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val newWidth: Int
        val newHeight: Int

        if (ratio > 1) {
            // Landscape
            newWidth = maxWidth
            newHeight = (maxWidth / ratio).toInt()
        } else {
            // Portrait
            newHeight = maxHeight
            newWidth = (maxHeight * ratio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Compress bitmap to JPEG byte array with quality optimization
     * Target: 100-150KB
     */
    private fun compressToByteArray(bitmap: Bitmap): ByteArray {
        var quality = 80
        var compressed: ByteArray

        do {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            compressed = outputStream.toByteArray()

            // If still too large, reduce quality
            if (compressed.size > 200000 && quality > 20) {
                quality -= 10
            } else {
                break
            }
        } while (compressed.size > 200000)

        return compressed
    }

    /**
     * Generate unique ID for image
     */
    fun generateImageId(): String = UUID.randomUUID().toString()

    /**
     * Generate filename for image with timestamp
     */
    fun generateImageFilename(): String {
        val timestamp = System.currentTimeMillis()
        return "img_$timestamp.jpg"
    }

    /**
     * Decode Base64 string to Bitmap
     */
    fun decodeBase64ToBitmap(base64String: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64String, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Convert Base64 string to raw byte array (JPEG/PNG bytes)
     */
    fun base64ToByteArray(base64String: String): ByteArray? {
        return try {
            Base64.decode(base64String, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Save Base64 image bytes to a cache file and return the File.
     * If the file already exists, it will be returned as-is.
     */
    fun base64ToCachedFile(context: Context, base64String: String, filename: String): File? {
        return try {
            val bytes = base64ToByteArray(base64String) ?: return null
            val imagesDir = File(context.cacheDir, "diary_images")
            if (!imagesDir.exists()) imagesDir.mkdirs()
            val outFile = File(imagesDir, filename)
            if (!outFile.exists()) {
                FileOutputStream(outFile).use { fos ->
                    fos.write(bytes)
                    fos.flush()
                }
            }
            outFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
