package com.example.ocr_finace

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.ocr_finace.data.ReceiptDatabase
import com.example.ocr_finace.data.ReceiptRepository
import com.example.ocr_finace.image.ImagePreprocessor
import com.example.ocr_finace.image.ReceiptImageStore
import com.example.ocr_finace.network.LmStudioApi
import com.example.ocr_finace.network.HomeNetworkManager
import com.example.ocr_finace.processing.LmStudioReceiptProcessor
import com.example.ocr_finace.processing.OcrQueue
import com.example.ocr_finace.settings.LmStudioSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FinanceApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(this, ReceiptDatabase::class.java, "receipts.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
        val settings = LmStudioSettings(this)
        val imageStore = ReceiptImageStore(this)
        val api = LmStudioApi()
        val homeNetwork = HomeNetworkManager(this)
        val processor = LmStudioReceiptProcessor(settings, api, ImagePreprocessor())
        val receipts = ReceiptRepository(database.receiptDao(), imageStore, processor)
        container = AppContainer(
            receipts = receipts,
            settings = settings,
            api = api,
            ocrQueue = OcrQueue(this, receipts, settings),
            homeNetwork = homeNetwork,
        )
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            container.ocrQueue.recoverInterrupted()
        }
    }
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE receipts ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE receipts ADD COLUMN processingAttempt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE receipts ADD COLUMN lastAttemptedAt INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE receipts ADD COLUMN lastPromptType TEXT NOT NULL DEFAULT 'STANDARD'")
    }
}

data class AppContainer(
    val receipts: ReceiptRepository,
    val settings: LmStudioSettings,
    val api: LmStudioApi,
    val ocrQueue: OcrQueue,
    val homeNetwork: HomeNetworkManager,
)
