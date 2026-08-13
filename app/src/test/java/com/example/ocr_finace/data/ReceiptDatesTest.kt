package com.example.ocr_finace.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.TimeZone

class ReceiptDatesTest {
    @Test
    fun addedDateRoundTrips() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val timestamp = parseAddedDate("2026-08-12")

            assertEquals("2026-08-12", formatAddedDate(timestamp))
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun invalidAddedDateIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            parseAddedDate("2026-02-30")
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseAddedDate("2026-08-12 extra")
        }
    }
}
