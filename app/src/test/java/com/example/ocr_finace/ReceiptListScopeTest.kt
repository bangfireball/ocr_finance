package com.example.ocr_finace

import com.example.ocr_finace.data.ReceiptEntity
import com.example.ocr_finace.data.ProcessingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptListScopeTest {
    private val active = receipt("active", isArchived = false)
    private val archived = receipt("archived", isArchived = true)

    @Test
    fun activeScopeExcludesArchivedReceipts() {
        assertEquals(
            listOf(active),
            options(listOf(active, archived), ReceiptListScope.ACTIVE),
        )
    }

    @Test
    fun archivedScopeExcludesActiveReceipts() {
        assertEquals(
            listOf(archived),
            options(listOf(active, archived), ReceiptListScope.ARCHIVED),
        )
    }

    @Test
    fun allScopePreservesEveryReceipt() {
        assertEquals(
            listOf(active, archived),
            options(listOf(active, archived), ReceiptListScope.ALL),
        )
    }

    @Test
    fun searchMatchesMerchantAndOcrText() {
        val hardware = receipt("hardware", false, merchant = "Home Depot", rawText = "Hartford CT")
        assertEquals(listOf(hardware), options(listOf(active, hardware), query = "depot"))
        assertEquals(listOf(hardware), options(listOf(active, hardware), query = "hartford"))
    }

    @Test
    fun statusAndTotalSortCanBeCombined() {
        val lower = receipt("lower", false, total = "12.50", status = ProcessingStatus.COMPLETE.name)
        val higher = receipt("higher", false, total = "\$104.20", status = ProcessingStatus.COMPLETE.name)
        val failed = receipt("failed", false, total = "999", status = ProcessingStatus.FAILED.name)

        assertEquals(
            listOf(higher, lower),
            options(
                listOf(lower, failed, higher),
                sort = ReceiptSort.TOTAL_HIGH,
                status = ReceiptStatusFilter.READY,
            ),
        )
    }

    @Test
    fun newestAndOldestUseReversedDateOrdering() {
        val earlier = receipt("earlier", false, date = "2025-01-01")
        val later = receipt("later", false, date = "2026-01-01")

        assertEquals(
            listOf(earlier, later),
            options(listOf(later, earlier), sort = ReceiptSort.DATE_NEWEST),
        )
        assertEquals(
            listOf(later, earlier),
            options(listOf(earlier, later), sort = ReceiptSort.DATE_OLDEST),
        )
    }

    @Test
    fun advancedFiltersCanBeCombined() {
        val match = receipt(
            "match",
            false,
            date = "2026-04-15",
            source = "CAMERA",
            currency = "USD",
            cashewExportedAt = 1L,
        )
        val outsideDate = receipt(
            "outside",
            false,
            date = "2025-12-01",
            source = "CAMERA",
            currency = "USD",
            cashewExportedAt = 1L,
        )

        assertEquals(
            listOf(match),
            applyReceiptListOptions(
                receipts = listOf(outsideDate, match),
                scope = ReceiptListScope.ALL,
                query = "",
                sort = ReceiptSort.DATE_NEWEST,
                statusFilter = ReceiptStatusFilter.ANY,
                sourceFilter = ReceiptSourceFilter.CAMERA,
                cashewFilter = CashewStatusFilter.SENT,
                currency = "usd",
                dateFrom = "2026-01-01",
                dateTo = "2026-12-31",
            ),
        )
    }

    private fun options(
        receipts: List<ReceiptEntity>,
        scope: ReceiptListScope = ReceiptListScope.ALL,
        query: String = "",
        sort: ReceiptSort = ReceiptSort.DATE_NEWEST,
        status: ReceiptStatusFilter = ReceiptStatusFilter.ANY,
    ) = applyReceiptListOptions(receipts, scope, query, sort, status)

    private fun receipt(
        id: String,
        isArchived: Boolean,
        merchant: String = "",
        rawText: String = "",
        total: String = "",
        status: String = ProcessingStatus.PENDING.name,
        date: String = "",
        source: String = "IMPORT",
        currency: String = "",
        cashewExportedAt: Long? = null,
    ) = ReceiptEntity(
        id = id,
        imagePath = "/tmp/$id.jpg",
        source = source,
        createdAt = 1L,
        updatedAt = 1L,
        merchantName = merchant,
        rawOcrText = rawText,
        total = total,
        processingStatus = status,
        transactionDate = date,
        currency = currency,
        cashewExportedAt = cashewExportedAt,
        isArchived = isArchived,
    )
}
