package com.example.ocr_finace.processing

import com.example.ocr_finace.image.ImagePreprocessor
import com.example.ocr_finace.data.OcrPromptType
import com.example.ocr_finace.network.LmStudioApi
import com.example.ocr_finace.settings.LmStudioSettings
import org.json.JSONObject
import java.io.File

class LmStudioReceiptProcessor(
    private val settings: LmStudioSettings,
    private val api: LmStudioApi,
    private val imagePreprocessor: ImagePreprocessor,
) : ReceiptProcessor {
    override suspend fun process(image: File, promptType: OcrPromptType): ProcessedReceipt {
        val response = api.analyze(settings.load(), imagePreprocessor.asDataUrl(image), promptType)
        val cleaned = response.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return runCatching {
            val json = JSONObject(cleaned)
            ProcessedReceipt(
                rawText = json.optString("rawText"),
                merchant = json.optString("merchant"),
                date = json.optString("date"),
                subtotal = json.optString("subtotal"),
                tax = json.optString("tax"),
                total = json.optString("total"),
                currency = json.optString("currency"),
            )
        }.getOrElse { error("LM Studio did not return valid receipt JSON") }
    }
}
