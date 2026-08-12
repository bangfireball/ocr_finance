package com.example.ocr_finace.data

import android.net.Uri
import com.example.ocr_finace.image.ReceiptImageStore
import com.example.ocr_finace.processing.ReceiptProcessor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CancellationException
import java.io.File

class ReceiptRepository(
    private val dao: ReceiptDao,
    private val imageStore: ReceiptImageStore,
    private val processor: ReceiptProcessor,
) {
    fun observeAll(): Flow<List<ReceiptEntity>> = dao.observeAll()
    fun observe(id: String): Flow<ReceiptEntity?> = dao.observe(id)
    suspend fun get(id: String): ReceiptEntity? = dao.get(id)
    suspend fun getProcessingReceipts(): List<ReceiptEntity> =
        dao.getByStatus(ProcessingStatus.PROCESSING.name)

    fun prepareCapture(): Pair<String, Uri> = imageStore.createCapture()

    suspend fun finishCapture(id: String): ReceiptEntity = create(id, imageStore.imageFile(id), ReceiptSource.CAMERA)

    suspend fun importImage(uri: Uri): ReceiptEntity {
        val (id, file) = imageStore.importImage(uri)
        return create(id, file, ReceiptSource.IMPORT)
    }

    fun cancelCapture(id: String) = imageStore.delete(id)

    suspend fun markQueued(id: String) {
        val receipt = dao.get(id) ?: return
        dao.update(
            receipt.copy(
                processingStatus = ProcessingStatus.QUEUED.name,
                processingError = null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun process(id: String, promptType: OcrPromptType) {
        val receipt = dao.get(id) ?: return
        val attemptedAt = System.currentTimeMillis()
        dao.update(
            receipt.copy(
                processingStatus = ProcessingStatus.PROCESSING.name,
                processingError = null,
                processingAttempt = receipt.processingAttempt + 1,
                lastAttemptedAt = attemptedAt,
                lastPromptType = promptType.name,
                updatedAt = attemptedAt,
            ),
        )
        runCatching { processor.process(File(receipt.imagePath), promptType) }
            .onSuccess { result ->
                val current = dao.get(id) ?: return@onSuccess
                val resultFields = applyProcessedResultIfUnchanged(current, attemptedAt, result)
                dao.update(
                    resultFields.copy(
                        processingStatus = ProcessingStatus.COMPLETE.name,
                        processingError = null,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
            .onFailure { error ->
                if (error is CancellationException) throw error
                val current = dao.get(id) ?: return@onFailure
                dao.update(
                    current.copy(
                        processingStatus = ProcessingStatus.FAILED.name,
                        processingError = error.message ?: "Receipt processing failed",
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
    }

    suspend fun updateFields(
        id: String,
        merchant: String,
        date: String,
        subtotal: String,
        tax: String,
        total: String,
        currency: String,
        rawText: String,
    ) {
        val receipt = dao.get(id) ?: return
        dao.update(
            receipt.copy(
                merchantName = merchant,
                transactionDate = date,
                subtotal = subtotal,
                tax = tax,
                total = total,
                currency = currency,
                rawOcrText = rawText,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun markCashewOpened(id: String) {
        val receipt = dao.get(id) ?: return
        dao.update(receipt.copy(cashewExportedAt = System.currentTimeMillis()))
    }

    suspend fun archive(id: String) {
        val receipt = dao.get(id) ?: return
        dao.update(receipt.copy(isArchived = true, updatedAt = System.currentTimeMillis()))
    }

    suspend fun restore(id: String) {
        val receipt = dao.get(id) ?: return
        dao.update(receipt.copy(isArchived = false, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: String) {
        dao.delete(id)
        imageStore.delete(id)
    }

    private suspend fun create(id: String, file: File, source: ReceiptSource): ReceiptEntity {
        val now = System.currentTimeMillis()
        val receipt = ReceiptEntity(
            id = id,
            imagePath = file.path,
            source = source.name,
            createdAt = now,
            updatedAt = now,
        )
        dao.insert(receipt)
        return receipt
    }
}

internal fun ReceiptEntity.withProcessedFields(result: com.example.ocr_finace.processing.ProcessedReceipt) = copy(
    merchantName = result.merchant,
    transactionDate = result.date,
    subtotal = result.subtotal,
    tax = result.tax,
    total = result.total,
    currency = result.currency,
    rawOcrText = result.rawText,
)

internal fun applyProcessedResultIfUnchanged(
    current: ReceiptEntity,
    processingRevision: Long,
    result: com.example.ocr_finace.processing.ProcessedReceipt,
): ReceiptEntity = if (current.updatedAt == processingRevision) {
    current.withProcessedFields(result)
} else {
    current
}
