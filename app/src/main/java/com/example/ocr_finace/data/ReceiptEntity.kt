package com.example.ocr_finace.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "receipts")
data class ReceiptEntity(
    @PrimaryKey val id: String,
    val imagePath: String,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
    val merchantName: String = "",
    val transactionDate: String = "",
    val subtotal: String = "",
    val tax: String = "",
    val total: String = "",
    val currency: String = "",
    val rawOcrText: String = "",
    val processingStatus: String = ProcessingStatus.PENDING.name,
    val processingError: String? = null,
    val cashewExportedAt: Long? = null,
    val isArchived: Boolean = false,
    val processingAttempt: Int = 0,
    val lastAttemptedAt: Long? = null,
    val lastPromptType: String = OcrPromptType.STANDARD.name,
)

enum class ProcessingStatus { PENDING, QUEUED, PROCESSING, COMPLETE, FAILED }

enum class ReceiptSource { CAMERA, IMPORT }

enum class OcrPromptType { STANDARD, SECOND_ATTEMPT }
