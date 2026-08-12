package com.example.ocr_finace.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrQueueSettingsTest {
    @Test
    fun oneConcurrentJobAlwaysUsesSingleSlot() {
        repeat(10) { sequence ->
            assertEquals(0, queueSlot(sequence.toLong(), 1))
        }
    }

    @Test
    fun jobsAreDistributedAcrossConfiguredSlots() {
        assertEquals(listOf(0, 1, 2, 0, 1, 2), (0L..5L).map { queueSlot(it, 3) })
    }

    @Test
    fun invalidConcurrencyFallsBackToOneSlot() {
        assertEquals(0, queueSlot(7L, 0))
    }
}
