package com.example.ocr_finace.data

import com.example.ocr_finace.processing.ProcessedReceipt
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptRevisionTest {
    @Test
    fun processedFieldsReplaceReceiptWhenRevisionIsUnchanged() {
        val receipt = receipt(merchant = "Old merchant")
        val updated = applyProcessedResultIfUnchanged(
            receipt,
            processingRevision = receipt.updatedAt,
            result = ProcessedReceipt(rawText = "New text", merchant = "New merchant", total = "42.00"),
        )

        assertEquals("New merchant", updated.merchantName)
        assertEquals("42.00", updated.total)
        assertEquals("New text", updated.rawOcrText)
    }

    @Test
    fun userFieldsArePreservedWhenRevisionChangedDuringProcessing() {
        val edited = receipt(merchant = "User correction")
        val result = applyProcessedResultIfUnchanged(
            edited,
            processingRevision = edited.updatedAt - 1L,
            result = ProcessedReceipt(
                rawText = "Model text",
                merchant = "Model merchant",
                total = "99.00",
            ),
        )

        assertEquals("User correction", result.merchantName)
        assertEquals("", result.total)
        assertEquals("", result.rawOcrText)
    }

    private fun receipt(merchant: String) = ReceiptEntity(
        id = "receipt-id",
        imagePath = "/receipt.jpg",
        source = ReceiptSource.IMPORT.name,
        createdAt = 1L,
        updatedAt = 2L,
        merchantName = merchant,
        processingStatus = ProcessingStatus.PROCESSING.name,
    )
}
