package com.example.ocr_finace.integration.cashew

import android.net.Uri
import com.example.ocr_finace.data.ReceiptEntity
import com.example.ocr_finace.settings.CashewExportConfig

object CashewLinkBuilder {
    fun build(receipt: ReceiptEntity, config: CashewExportConfig): Uri {
        val builder = Uri.parse("https://cashewapp.web.app/addTransaction")
            .buildUpon()
            .appendQueryParameter("amount", receipt.total.filter { it.isDigit() || it == '.' || it == ',' })
        cashewOptionalParameters(receipt, config).forEach(builder::appendQueryParameter)
        return builder.build()
    }
}

internal fun cashewOptionalParameters(
    receipt: ReceiptEntity,
    config: CashewExportConfig,
): Map<String, String> = buildMap {
    if (config.includeTitle) put("title", receipt.merchantName.ifBlank { "Receipt" })
    if (config.includeDate && receipt.transactionDate.isNotBlank()) put("date", receipt.transactionDate)
    val notes = buildList {
        if (config.includeReceiptReference) add("OCR Finance receipt ${receipt.id}")
        if (config.includeOcrText && receipt.rawOcrText.isNotBlank()) add(receipt.rawOcrText)
    }.joinToString("\n\n")
    if (notes.isNotBlank()) put("notes", notes)
}
