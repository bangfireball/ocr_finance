package com.example.ocr_finace.ui.receipt

sealed interface ReceiptDestination {
    data object List : ReceiptDestination
    data object Settings : ReceiptDestination
    data object CashewSettings : ReceiptDestination
    data class Detail(val receiptId: String) : ReceiptDestination

    fun encode(): String = when (this) {
        List -> LIST
        Settings -> SETTINGS
        CashewSettings -> CASHEW_SETTINGS
        is Detail -> "$DETAIL_PREFIX$receiptId"
    }

    companion object {
        private const val LIST = "list"
        private const val SETTINGS = "settings"
        private const val CASHEW_SETTINGS = "cashew-settings"
        private const val DETAIL_PREFIX = "detail:"

        fun decode(value: String): ReceiptDestination = when {
            value == SETTINGS -> Settings
            value == CASHEW_SETTINGS -> CashewSettings
            value.startsWith(DETAIL_PREFIX) -> value.removePrefix(DETAIL_PREFIX)
                .takeIf(String::isNotBlank)
                ?.let(::Detail)
                ?: List
            else -> List
        }
    }
}
