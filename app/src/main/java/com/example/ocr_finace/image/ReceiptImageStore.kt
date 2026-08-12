package com.example.ocr_finace.image

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

class ReceiptImageStore(private val context: Context) {
    private val root = File(context.filesDir, "receipts").apply { mkdirs() }

    fun createCapture(): Pair<String, Uri> {
        val id = UUID.randomUUID().toString()
        val file = imageFile(id)
        file.parentFile?.mkdirs()
        return id to FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    fun imageFile(id: String): File = File(root, "$id/original.jpg")

    fun importImage(uri: Uri): Pair<String, File> {
        val id = UUID.randomUUID().toString()
        val target = imageFile(id)
        target.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open the selected image" }
            target.outputStream().use(input::copyTo)
        }
        return id to target
    }

    fun delete(id: String) {
        imageFile(id).parentFile?.deleteRecursively()
    }
}
