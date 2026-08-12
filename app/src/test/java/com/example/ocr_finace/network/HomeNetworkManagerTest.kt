package com.example.ocr_finace.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeNetworkManagerTest {
    @Test
    fun removesPlatformQuotesFromSsid() {
        assertEquals("My WiFi", normalizeSsid("\"My WiFi\""))
    }

    @Test
    fun rejectsUnavailableSsidValues() {
        assertNull(normalizeSsid(null))
        assertNull(normalizeSsid(""))
        assertNull(normalizeSsid("<unknown ssid>"))
    }
}
