package com.example.ocr_finace.integration.cashew

import com.example.ocr_finace.data.ReceiptEntity
import com.example.ocr_finace.settings.CashewExportConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CashewExportTest {
    private val receipt = ReceiptEntity(
        id = "receipt-1",
        imagePath = "/tmp/receipt.jpg",
        source = "IMPORT",
        createdAt = 1L,
        updatedAt = 1L,
        merchantName = "Home Depot",
        transactionDate = "2026-08-12",
        total = "42.50",
        rawOcrText = "raw receipt text",
    )

    @Test
    fun enabledFieldsAreMappedToCashewDestinations() {
        val parameters = cashewOptionalParameters(
            receipt,
            CashewExportConfig(includeOcrText = true),
        )

        assertEquals("Home Depot", parameters["title"])
        assertEquals("2026-08-12", parameters["date"])
        assertTrue(parameters.getValue("notes").contains("OCR Finance receipt receipt-1"))
        assertTrue(parameters.getValue("notes").contains("raw receipt text"))
    }

    @Test
    fun disabledOptionalFieldsAreOmitted() {
        val parameters = cashewOptionalParameters(
            receipt,
            CashewExportConfig(false, false, false, false),
        )

        assertFalse(parameters.containsKey("title"))
        assertFalse(parameters.containsKey("date"))
        assertFalse(parameters.containsKey("notes"))
    }
}
