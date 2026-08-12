package com.example.ocr_finace.processing

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.BackoffPolicy
import com.example.ocr_finace.FinanceApplication
import com.example.ocr_finace.data.ReceiptRepository
import com.example.ocr_finace.data.OcrPromptType
import com.example.ocr_finace.settings.LmStudioSettings
import java.util.concurrent.TimeUnit

class OcrQueue(
    context: Context,
    private val repository: ReceiptRepository,
    private val settings: LmStudioSettings,
) {
    private val workManager = WorkManager.getInstance(context)

    suspend fun enqueue(
        receiptId: String,
        promptType: OcrPromptType = OcrPromptType.STANDARD,
    ) {
        repository.markQueued(receiptId)
        val slot = settings.nextOcrSlot()
        val request = OneTimeWorkRequestBuilder<ReceiptOcrWorker>()
            .setInputData(
                Data.Builder()
                    .putString(RECEIPT_ID, receiptId)
                    .putString(PROMPT_TYPE, promptType.name)
                    .build(),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(requiredNetworkType(settings.loadHomeNetwork().enabled))
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
            .addTag(receiptTag(receiptId))
            .build()
        workManager.enqueueUniqueWork(
            "ocr-finance-slot-$slot",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    fun cancel(receiptId: String) {
        workManager.cancelAllWorkByTag(receiptTag(receiptId))
    }

    suspend fun recoverInterrupted() {
        repository.getProcessingReceipts().forEach { receipt ->
            val states = workManager.getWorkInfosByTag(receiptTag(receipt.id)).get()
                .map { it.state }
            if (!states.any(::isUnfinishedWork)) {
                enqueue(
                    receiptId = receipt.id,
                    promptType = runCatching { OcrPromptType.valueOf(receipt.lastPromptType) }
                        .getOrDefault(OcrPromptType.STANDARD),
                )
            }
        }
    }

    private fun receiptTag(receiptId: String) = "ocr-finance-receipt-$receiptId"

    companion object {
        const val RECEIPT_ID = "receipt_id"
        const val PROMPT_TYPE = "prompt_type"
    }
}

class ReceiptOcrWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val receiptId = inputData.getString(OcrQueue.RECEIPT_ID) ?: return Result.failure()
        val promptType = runCatching {
            OcrPromptType.valueOf(
                inputData.getString(OcrQueue.PROMPT_TYPE) ?: OcrPromptType.STANDARD.name,
            )
        }.getOrDefault(OcrPromptType.STANDARD)
        val application = applicationContext as FinanceApplication
        val homeConfig = application.container.settings.loadHomeNetwork()
        if (homeConfig.enabled && !application.container.api.canConnect(
                application.container.settings.load(),
            )
        ) {
            return Result.retry()
        }
        application.container.receipts.process(receiptId, promptType)
        return Result.success()
    }
}

internal fun requiredNetworkType(homeOnly: Boolean): NetworkType =
    if (homeOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

internal fun isUnfinishedWork(state: WorkInfo.State): Boolean = !state.isFinished
