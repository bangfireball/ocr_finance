package com.example.ocr_finace.processing

import com.example.ocr_finace.data.OcrPromptType
import java.io.File

data class ProcessedReceipt(
    val rawText: String,
    val merchant: String = "",
    val date: String = "",
    val subtotal: String = "",
    val tax: String = "",
    val total: String = "",
    val currency: String = "",
)

interface ReceiptProcessor {
    suspend fun process(image: File, promptType: OcrPromptType): ProcessedReceipt
}
