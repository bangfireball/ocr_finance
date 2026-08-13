package com.example.ocr_finace.processing

import com.example.ocr_finace.image.ImagePreprocessor
import com.example.ocr_finace.data.OcrPromptType
import com.example.ocr_finace.network.LmStudioApi
import com.example.ocr_finace.settings.LmStudioSettings
import java.io.File

class LmStudioReceiptProcessor(
    private val settings: LmStudioSettings,
    private val api: LmStudioApi,
    private val imagePreprocessor: ImagePreprocessor,
) : ReceiptProcessor {
    override suspend fun process(image: File, promptType: OcrPromptType): ProcessedReceipt {
        val receiptId = image.parentFile?.name.orEmpty()
        val crop = receiptId.takeIf(String::isNotBlank)?.let(settings::loadSavedCropSelection)
        val preparedOutput = File(image.parentFile, "lm-input.jpg")
        val response = api.analyze(
            settings.load(),
            imagePreprocessor.asDataUrl(image, crop, preparedOutput = preparedOutput),
            promptType,
        )
        return parseReceiptResponse(response)
    }
}
