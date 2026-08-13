package com.example.ocr_finace.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeConfigTest {
    @Test
    fun confirmationDefaultsToEnabled() {
        val config = SwipeConfig(SwipeAction.ARCHIVE, SwipeAction.DELETE)

        assertTrue(config.confirmActions)
    }

    @Test
    fun cashewIsAvailableAsSwipeAction() {
        assertEquals(SwipeAction.CASHEW, SwipeAction.valueOf("CASHEW"))
    }
}
