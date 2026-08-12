package com.example.ocr_finace.ui.receipt

import com.example.ocr_finace.data.ProcessingStatus
import com.example.ocr_finace.data.ReceiptEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptDisplayTest {
    @Test
    fun usesMerchantForEveryProcessingState() {
        ProcessingStatus.entries.forEach { status ->
            assertEquals(
                "THE HOME DEPOT",
                receiptDisplayTitle(receipt(merchant = "  THE HOME DEPOT  ", status = status)),
            )
        }
    }

    @Test
    fun describesReceiptWithoutMerchantByProcessingState() {
        assertEquals("Queued receipt", receiptDisplayTitle(receipt(status = ProcessingStatus.PENDING)))
        assertEquals("Queued receipt", receiptDisplayTitle(receipt(status = ProcessingStatus.QUEUED)))
        assertEquals("Processing receipt", receiptDisplayTitle(receipt(status = ProcessingStatus.PROCESSING)))
        assertEquals(
            "Receipt processing failed",
            receiptDisplayTitle(receipt(status = ProcessingStatus.FAILED)),
        )
        assertEquals("Receipt", receiptDisplayTitle(receipt(status = ProcessingStatus.COMPLETE)))
    }

    private fun receipt(
        merchant: String = "",
        status: ProcessingStatus,
    ) = ReceiptEntity(
        id = "receipt-id",
        imagePath = "/receipt.jpg",
        source = "IMPORT",
        createdAt = 1L,
        updatedAt = 1L,
        merchantName = merchant,
        processingStatus = status.name,
    )
}
