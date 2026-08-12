package com.example.ocr_finace.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {
    @Query("SELECT * FROM receipts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts WHERE id = :id")
    fun observe(id: String): Flow<ReceiptEntity?>

    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun get(id: String): ReceiptEntity?

    @Query("SELECT * FROM receipts WHERE processingStatus = :status")
    suspend fun getByStatus(status: String): List<ReceiptEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(receipt: ReceiptEntity)

    @Update
    suspend fun update(receipt: ReceiptEntity)

    @Query("DELETE FROM receipts WHERE id = :id")
    suspend fun delete(id: String)
}
