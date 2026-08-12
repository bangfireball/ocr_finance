package com.example.ocr_finace.settings

import android.content.Context

data class LmStudioConfig(
    val baseUrl: String,
    val model: String,
    val apiToken: String,
)

enum class SwipeAction { ARCHIVE, DELETE }

data class SwipeConfig(
    val right: SwipeAction,
    val left: SwipeAction,
)

data class HomeNetworkConfig(
    val enabled: Boolean,
    val ssid: String,
)

enum class ThemeMode { FOLLOW_DEVICE, LIGHT, DARK }

enum class ReceiptLayoutMode { THUMBNAIL, LIST }

data class CashewExportConfig(
    val includeTitle: Boolean = true,
    val includeDate: Boolean = true,
    val includeReceiptReference: Boolean = true,
    val includeOcrText: Boolean = false,
)

class LmStudioSettings(context: Context) {
    private val preferences = context.getSharedPreferences("lm_studio", Context.MODE_PRIVATE)

    fun load(): LmStudioConfig = LmStudioConfig(
        baseUrl = preferences.getString("base_url", DEFAULT_URL) ?: DEFAULT_URL,
        model = preferences.getString("model", "") ?: "",
        apiToken = preferences.getString("api_token", "") ?: "",
    )

    fun save(config: LmStudioConfig) {
        preferences.edit()
            .putString("base_url", config.baseUrl.trim().trimEnd('/'))
            .putString("model", config.model.trim())
            .putString("api_token", config.apiToken.trim())
            .apply()
    }

    fun loadSwipeConfig(): SwipeConfig = SwipeConfig(
        right = loadSwipeAction("swipe_right", SwipeAction.ARCHIVE),
        left = loadSwipeAction("swipe_left", SwipeAction.DELETE),
    )

    fun saveSwipeConfig(config: SwipeConfig) {
        preferences.edit()
            .putString("swipe_right", config.right.name)
            .putString("swipe_left", config.left.name)
            .apply()
    }

    fun loadOcrConcurrency(): Int = preferences.getInt("ocr_concurrency", DEFAULT_OCR_CONCURRENCY)
        .coerceIn(MIN_OCR_CONCURRENCY, MAX_OCR_CONCURRENCY)

    fun saveOcrConcurrency(value: Int) {
        preferences.edit()
            .putInt("ocr_concurrency", value.coerceIn(MIN_OCR_CONCURRENCY, MAX_OCR_CONCURRENCY))
            .apply()
    }

    fun loadHomeNetwork(): HomeNetworkConfig = HomeNetworkConfig(
        enabled = preferences.getBoolean("home_network_enabled", false),
        ssid = preferences.getString("home_network_ssid", "").orEmpty(),
    )

    fun saveHomeNetwork(config: HomeNetworkConfig) {
        preferences.edit()
            .putBoolean("home_network_enabled", config.enabled && config.ssid.isNotBlank())
            .putString("home_network_ssid", config.ssid.trim())
            .apply()
    }

    fun forgetHomeNetwork() {
        preferences.edit()
            .putBoolean("home_network_enabled", false)
            .remove("home_network_ssid")
            .apply()
    }

    fun loadThemeMode(): ThemeMode = runCatching {
        ThemeMode.valueOf(
            preferences.getString("theme_mode", ThemeMode.FOLLOW_DEVICE.name)
                ?: ThemeMode.FOLLOW_DEVICE.name,
        )
    }.getOrDefault(ThemeMode.FOLLOW_DEVICE)

    fun saveThemeMode(mode: ThemeMode) {
        preferences.edit().putString("theme_mode", mode.name).apply()
    }

    fun loadReceiptLayoutMode(): ReceiptLayoutMode = runCatching {
        ReceiptLayoutMode.valueOf(
            preferences.getString("receipt_layout", ReceiptLayoutMode.THUMBNAIL.name)
                ?: ReceiptLayoutMode.THUMBNAIL.name,
        )
    }.getOrDefault(ReceiptLayoutMode.THUMBNAIL)

    fun saveReceiptLayoutMode(mode: ReceiptLayoutMode) {
        preferences.edit().putString("receipt_layout", mode.name).apply()
    }

    fun loadCashewExportConfig(): CashewExportConfig = CashewExportConfig(
        includeTitle = preferences.getBoolean("cashew_title", true),
        includeDate = preferences.getBoolean("cashew_date", true),
        includeReceiptReference = preferences.getBoolean("cashew_reference", true),
        includeOcrText = preferences.getBoolean("cashew_ocr_text", false),
    )

    fun saveCashewExportConfig(config: CashewExportConfig) {
        preferences.edit()
            .putBoolean("cashew_title", config.includeTitle)
            .putBoolean("cashew_date", config.includeDate)
            .putBoolean("cashew_reference", config.includeReceiptReference)
            .putBoolean("cashew_ocr_text", config.includeOcrText)
            .apply()
    }

    @Synchronized
    fun nextOcrSlot(): Int {
        val sequence = preferences.getLong("ocr_queue_sequence", 0L)
        preferences.edit().putLong("ocr_queue_sequence", sequence + 1L).apply()
        return queueSlot(sequence, loadOcrConcurrency())
    }

    private fun loadSwipeAction(key: String, default: SwipeAction): SwipeAction = runCatching {
        SwipeAction.valueOf(preferences.getString(key, default.name) ?: default.name)
    }.getOrDefault(default)

    companion object {
        const val DEFAULT_URL = "http://10.0.2.2:1234"
        const val DEFAULT_OCR_CONCURRENCY = 1
        const val MIN_OCR_CONCURRENCY = 1
        const val MAX_OCR_CONCURRENCY = 4
    }
}

internal fun queueSlot(sequence: Long, concurrency: Int): Int =
    Math.floorMod(sequence, concurrency.coerceAtLeast(1).toLong()).toInt()
