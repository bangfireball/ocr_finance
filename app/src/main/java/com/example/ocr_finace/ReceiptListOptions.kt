package com.example.ocr_finace

import com.example.ocr_finace.data.ProcessingStatus
import com.example.ocr_finace.data.ReceiptEntity
import java.util.Locale

internal enum class ReceiptListScope { ACTIVE, ARCHIVED, ALL }

internal enum class ReceiptSort {
    DATE_NEWEST,
    DATE_OLDEST,
    MERCHANT_ASC,
    TOTAL_HIGH,
    TOTAL_LOW,
}

internal enum class ReceiptStatusFilter { ANY, READY, IN_PROGRESS, FAILED }

internal enum class ReceiptSourceFilter { ANY, CAMERA, IMPORTED }

internal enum class CashewStatusFilter { ANY, SENT, NOT_SENT }

internal fun applyReceiptListOptions(
    receipts: List<ReceiptEntity>,
    scope: ReceiptListScope,
    query: String,
    sort: ReceiptSort,
    statusFilter: ReceiptStatusFilter,
    sourceFilter: ReceiptSourceFilter = ReceiptSourceFilter.ANY,
    cashewFilter: CashewStatusFilter = CashewStatusFilter.ANY,
    currency: String = "",
    dateFrom: String = "",
    dateTo: String = "",
): List<ReceiptEntity> {
    val normalizedQuery = query.trim().lowercase(Locale.getDefault())
    val filtered = receipts.asSequence()
        .filter { receipt ->
            when (scope) {
                ReceiptListScope.ACTIVE -> !receipt.isArchived
                ReceiptListScope.ARCHIVED -> receipt.isArchived
                ReceiptListScope.ALL -> true
            }
        }
        .filter { receipt ->
            normalizedQuery.isEmpty() || listOf(
                receipt.merchantName,
                receipt.rawOcrText,
                receipt.transactionDate,
                receipt.total,
                receipt.currency,
            ).any { normalizedQuery in it.lowercase(Locale.getDefault()) }
        }
        .filter { receipt -> receipt.matches(statusFilter) }
        .filter { receipt -> receipt.matches(sourceFilter) }
        .filter { receipt -> receipt.matches(cashewFilter) }
        .filter { receipt ->
            currency.isBlank() || receipt.currency.equals(currency.trim(), ignoreCase = true)
        }
        .filter { receipt ->
            dateFrom.isBlank() || receipt.transactionDate.isNotBlank() && receipt.transactionDate >= dateFrom.trim()
        }
        .filter { receipt ->
            dateTo.isBlank() || receipt.transactionDate.isNotBlank() && receipt.transactionDate <= dateTo.trim()
        }
        .toList()

    return when (sort) {
        ReceiptSort.DATE_NEWEST -> filtered.sortedByDescending(ReceiptEntity::createdAt)
        ReceiptSort.DATE_OLDEST -> filtered.sortedBy(ReceiptEntity::createdAt)
        ReceiptSort.MERCHANT_ASC -> filtered.sortedWith(
            compareBy<ReceiptEntity> { it.merchantName.lowercase(Locale.getDefault()) }
                .thenByDescending(ReceiptEntity::createdAt),
        )
        ReceiptSort.TOTAL_HIGH -> filtered.sortedByDescending(::normalizedReceiptTotal)
        ReceiptSort.TOTAL_LOW -> filtered.sortedBy(::normalizedReceiptTotal)
    }
}

private fun ReceiptEntity.matches(filter: ReceiptStatusFilter): Boolean = when (filter) {
    ReceiptStatusFilter.ANY -> true
    ReceiptStatusFilter.READY -> processingStatus == ProcessingStatus.COMPLETE.name
    ReceiptStatusFilter.IN_PROGRESS -> processingStatus in setOf(
        ProcessingStatus.PENDING.name,
        ProcessingStatus.QUEUED.name,
        ProcessingStatus.PROCESSING.name,
    )
    ReceiptStatusFilter.FAILED -> processingStatus == ProcessingStatus.FAILED.name
}

private fun ReceiptEntity.matches(filter: ReceiptSourceFilter): Boolean = when (filter) {
    ReceiptSourceFilter.ANY -> true
    ReceiptSourceFilter.CAMERA -> source == "CAMERA"
    ReceiptSourceFilter.IMPORTED -> source == "IMPORT"
}

private fun ReceiptEntity.matches(filter: CashewStatusFilter): Boolean = when (filter) {
    CashewStatusFilter.ANY -> true
    CashewStatusFilter.SENT -> cashewExportedAt != null
    CashewStatusFilter.NOT_SENT -> cashewExportedAt == null
}

private fun normalizedReceiptTotal(receipt: ReceiptEntity): Double = receipt.total
    .replace(",", "")
    .filter { it.isDigit() || it == '.' || it == '-' }
    .toDoubleOrNull()
    ?: Double.NEGATIVE_INFINITY
