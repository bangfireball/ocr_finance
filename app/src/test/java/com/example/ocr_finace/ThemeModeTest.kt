package com.example.ocr_finace

import com.example.ocr_finace.settings.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test
    fun followDeviceUsesSystemValue() {
        assertTrue(isDarkTheme(ThemeMode.FOLLOW_DEVICE, systemDark = true))
        assertFalse(isDarkTheme(ThemeMode.FOLLOW_DEVICE, systemDark = false))
    }

    @Test
    fun explicitModesOverrideSystemValue() {
        assertTrue(isDarkTheme(ThemeMode.DARK, systemDark = false))
        assertFalse(isDarkTheme(ThemeMode.LIGHT, systemDark = true))
    }
}
