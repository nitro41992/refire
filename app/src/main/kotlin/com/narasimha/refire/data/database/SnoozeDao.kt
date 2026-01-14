package com.narasimha.refire.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SnoozeDao {
    @Query("SELECT * FROM snoozes ORDER BY snoozeEndTime ASC")
    fun getAllSnoozes(): Flow<List<SnoozeEntity>>

    @Query("SELECT * FROM snoozes WHERE status = 'ACTIVE' AND snoozeEndTime > :currentTime ORDER BY snoozeEndTime ASC")
    fun getActiveSnoozes(currentTime: Long): Flow<List<SnoozeEntity>>

    @Query("SELECT * FROM snoozes WHERE status IN ('EXPIRED', 'DISMISSED') ORDER BY snoozeEndTime DESC")
    fun getHistorySnoozes(): Flow<List<SnoozeEntity>>

    @Query("SELECT * FROM snoozes WHERE id = :id")
    suspend fun getSnoozeById(id: String): SnoozeEntity?

    @Query("SELECT * FROM snoozes WHERE threadId = :threadId AND status = 'ACTIVE' LIMIT 1")
    suspend fun getSnoozeByThread(threadId: String): SnoozeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnooze(snooze: SnoozeEntity)

    @Query("UPDATE snoozes SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE snoozes SET messagesJson = :messagesJson, suppressedCount = :suppressedCount WHERE id = :snoozeId")
    suspend fun updateMessagesAndSuppressedCount(snoozeId: String, messagesJson: String?, suppressedCount: Int)

    @Query("DELETE FROM snoozes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM snoozes WHERE threadId = :threadId AND status = 'ACTIVE'")
    suspend fun deleteActiveByThread(threadId: String)

    @Query("SELECT * FROM snoozes WHERE threadId = :threadId AND status IN ('EXPIRED', 'DISMISSED')")
    suspend fun getHistoryByThread(threadId: String): List<SnoozeEntity>

    @Query("DELETE FROM snoozes WHERE threadId = :threadId AND status IN ('EXPIRED', 'DISMISSED')")
    suspend fun deleteHistoryByThread(threadId: String)

    /**
     * Atomically replace any existing active snooze for a thread with a new one.
     * This ensures only one active snooze per thread and preserves history records.
     */
    @Transaction
    suspend fun replaceActiveSnoozeForThread(threadId: String, snooze: SnoozeEntity) {
        deleteActiveByThread(threadId)
        insertSnooze(snooze)
    }

    @Query("DELETE FROM snoozes WHERE status = 'ACTIVE' AND snoozeEndTime <= :currentTime")
    suspend fun deleteExpired(currentTime: Long): Int

    @Query("DELETE FROM snoozes WHERE status IN ('EXPIRED', 'DISMISSED') AND snoozeEndTime < :cutoffTime")
    suspend fun deleteOldHistory(cutoffTime: Long): Int

    @Query("DELETE FROM snoozes")
    suspend fun deleteAll()
}
