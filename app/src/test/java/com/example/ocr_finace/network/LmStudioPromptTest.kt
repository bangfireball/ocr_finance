package com.example.ocr_finace.network

import com.example.ocr_finace.data.OcrPromptType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LmStudioPromptTest {
    @Test
    fun standardPromptDoesNotClaimSecondAttempt() {
        assertFalse(
            LmStudioApi.promptFor(OcrPromptType.STANDARD)
                .contains("second independent reading"),
        )
    }

    @Test
    fun secondAttemptPromptRequestsFreshCarefulReadingWithoutDesiredAnswer() {
        val prompt = LmStudioApi.promptFor(OcrPromptType.SECOND_ATTEMPT)

        assertTrue(prompt.contains("second independent reading"))
        assertTrue(prompt.contains("Start again from the image"))
        assertTrue(prompt.contains("do not guess"))
        assertTrue(prompt.contains("rawText, merchant, date, subtotal, tax, total, currency"))
    }
}
