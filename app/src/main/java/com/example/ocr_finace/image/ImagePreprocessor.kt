package com.example.ocr_finace.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Canvas
import android.graphics.Paint
import android.media.ExifInterface
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

class ImagePreprocessor {
    fun asDataUrl(
        file: File,
        selection: CropSelection? = null,
        maxDimension: Int = 2200,
        preparedOutput: File? = null,
    ): String {
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
        val oriented = rotate(bitmap, exifRotation(file))
        val adjusted = if (selection == null) {
            oriented
        } else {
            val userRotated = rotate(oriented, selection.rotation.toFloat())
            val corrected = perspectiveCorrect(userRotated, selection.corners)
            if (userRotated !== oriented) userRotated.recycle()
            corrected
        }
        val scale = minOf(1f, maxDimension.toFloat() / maxOf(adjusted.width, adjusted.height))
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                adjusted,
                (adjusted.width * scale).toInt(),
                (adjusted.height * scale).toInt(),
                true,
            )
        } else adjusted

        val bytes = ByteArrayOutputStream().use { output ->
            check(resized.compress(Bitmap.CompressFormat.JPEG, 93, output))
            output.toByteArray()
        }
        preparedOutput?.let { output ->
            output.parentFile?.mkdirs()
            output.writeBytes(bytes)
        }
        if (resized !== adjusted) resized.recycle()
        if (adjusted !== oriented) adjusted.recycle()
        if (oriented !== bitmap) oriented.recycle()
        bitmap.recycle()
        return "data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
    }

    private fun perspectiveCorrect(bitmap: Bitmap, corners: List<NormalizedPoint>): Bitmap {
        require(isValidCrop(corners)) { "The document crop shape is invalid" }
        val points = corners.map { point ->
            point.x * bitmap.width to point.y * bitmap.height
        }
        fun distance(first: Pair<Float, Float>, second: Pair<Float, Float>): Float =
            kotlin.math.hypot(first.first - second.first, first.second - second.second)
        val outputWidth = maxOf(distance(points[0], points[1]), distance(points[3], points[2]))
            .toInt().coerceAtLeast(1)
        val outputHeight = maxOf(distance(points[0], points[3]), distance(points[1], points[2]))
            .toInt().coerceAtLeast(1)
        val source = points.flatMap { listOf(it.first, it.second) }.toFloatArray()
        val destination = floatArrayOf(
            0f, 0f,
            outputWidth.toFloat(), 0f,
            outputWidth.toFloat(), outputHeight.toFloat(),
            0f, outputHeight.toFloat(),
        )
        val transform = Matrix().apply {
            check(setPolyToPoly(source, 0, destination, 0, 4))
        }
        return Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888).also { output ->
            Canvas(output).drawBitmap(bitmap, transform, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
    }

    private fun exifRotation(file: File): Float = runCatching {
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

fun orientedImageDimensions(file: File, userRotation: Int = 0): Pair<Int, Int> {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, options)
    var width = options.outWidth.coerceAtLeast(1)
    var height = options.outHeight.coerceAtLeast(1)
    val exifRotation = runCatching {
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270 -> 90
            else -> 0
        }
    }.getOrDefault(0)
    if ((exifRotation + userRotation) % 180 != 0) {
        val previousWidth = width
        width = height
        height = previousWidth
    }
    return width to height
}
