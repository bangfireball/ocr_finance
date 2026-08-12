package com.example.ocr_finace.ui.receipt

import com.example.ocr_finace.data.ProcessingStatus
import com.example.ocr_finace.data.ReceiptEntity

fun receiptDisplayTitle(receipt: ReceiptEntity): String {
    val merchant = receipt.merchantName.trim()
    if (merchant.isNotEmpty()) return merchant

    return when (receipt.processingStatus) {
        ProcessingStatus.PENDING.name, ProcessingStatus.QUEUED.name -> "Queued receipt"
        ProcessingStatus.PROCESSING.name -> "Processing receipt"
        ProcessingStatus.FAILED.name -> "Receipt processing failed"
        else -> "Receipt"
    }
}
