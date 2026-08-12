package com.example.ocr_finace.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ReceiptEntity::class], version = 3, exportSchema = false)
abstract class ReceiptDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao
}
