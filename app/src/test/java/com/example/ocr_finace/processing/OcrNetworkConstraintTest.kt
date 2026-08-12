package com.example.ocr_finace.processing

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrNetworkConstraintTest {
    @Test
    fun unrestrictedOcrUsesAnyConnectedNetwork() {
        assertEquals(NetworkType.CONNECTED, requiredNetworkType(homeOnly = false))
    }

    @Test
    fun homeOnlyOcrRequiresUnmeteredNetwork() {
        assertEquals(NetworkType.UNMETERED, requiredNetworkType(homeOnly = true))
    }
}
