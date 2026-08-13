package com.example.ocr_finace.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReceiptResponseParserTest {
    @Test
    fun parsesFencedJsonWithSurroundingCommentaryAndNormalizesFields() {
        val result = parseReceiptResponse(
            """
            Here is the result:
            ```json
            {"rawText":"SHOP\nTOTAL ${'$'}1,234.50","merchant":" Corner Shop ","date":"8/12/2026","subtotal":"${'$'}1,200.00","tax":"USD 34.50","total":"1,234.50","currency":"usd"}
            ```
            """.trimIndent(),
        )

        assertEquals("Corner Shop", result.merchant)
        assertEquals("SHOP\nTOTAL ${'$'}1,234.50", result.rawText)
        assertEquals("2026-08-12", result.date)
        assertEquals("1200.00", result.subtotal)
        assertEquals("34.50", result.tax)
        assertEquals("1234.50", result.total)
        assertEquals("USD", result.currency)
    }

    @Test
    fun parsesEmptyAndNullFields() {
        val result = parseReceiptResponse(
            """{"rawText":"text","merchant":"Store","date":null,"subtotal":"","tax":"","total":12.5,"currency":"${'$'}"}""",
        )

        assertEquals("", result.date)
        assertEquals("12.50", result.total)
        assertEquals("USD", result.currency)
    }

    @Test
    fun reportsInvalidStructuredFields() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            parseReceiptResponse(
                """{"rawText":"text","merchant":"Store","date":"yesterday","subtotal":"","tax":"","total":"twelve","currency":"dollars maybe"}""",
            )
        }

        assertEquals(
            "LM Studio returned invalid receipt fields: date, total, currency. Try OCR again or edit the receipt manually.",
            error.message,
        )
    }

    @Test
    fun rejectsResponseWithoutReceiptJson() {
        val error = assertThrows(IllegalStateException::class.java) {
            parseReceiptResponse("I could not read this image.")
        }

        assertEquals("LM Studio response did not contain a recognizable receipt JSON object", error.message)
    }

    @Test
    fun normalizesCommonDatesAmountsAndCurrencies() {
        assertEquals("2026-08-12", normalizeReceiptDate("August 12, 2026"))
        assertEquals("-42.00", normalizeReceiptAmount("(42.00)"))
        assertEquals("1234.56", normalizeReceiptAmount("1.234,56 €"))
        assertEquals("EUR", normalizeReceiptCurrency("euro"))
        assertEquals(null, normalizeReceiptCurrency("US"))
    }
}
