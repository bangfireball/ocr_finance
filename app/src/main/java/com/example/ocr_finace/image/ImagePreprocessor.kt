package com.example.ocr_finace.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

class ImagePreprocessor {
    fun asDataUrl(file: File, maxDimension: Int = 1800): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The receipt image is invalid" }

        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > maxDimension * 2) {
            sample *= 2
        }
        val bitmap = requireNotNull(
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }),
        ) { "The receipt image could not be decoded" }
        val oriented = rotate(bitmap, rotation(file))
        val scale = minOf(1f, maxDimension.toFloat() / maxOf(oriented.width, oriented.height))
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                oriented,
                (oriented.width * scale).toInt(),
                (oriented.height * scale).toInt(),
                true,
            )
        } else oriented

        val bytes = ByteArrayOutputStream().use { output ->
            check(resized.compress(Bitmap.CompressFormat.JPEG, 86, output))
            output.toByteArray()
        }
        if (resized !== oriented) resized.recycle()
        if (oriented !== bitmap) oriented.recycle()
        bitmap.recycle()
        return "data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
    }

    private fun rotation(file: File): Float = runCatching {
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)

    private fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            Matrix().apply { postRotate(degrees) },
            true,
        )
    }
}
