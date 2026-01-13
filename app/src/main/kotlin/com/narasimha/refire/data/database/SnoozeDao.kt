package com.narasimha.refire.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("DELETE FROM snoozes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM snoozes WHERE threadId = :threadId")
    suspend fun deleteByThread(threadId: String)

    @Query("DELETE FROM snoozes WHERE status = 'ACTIVE' AND snoozeEndTime <= :currentTime")
    suspend fun deleteExpired(currentTime: Long): Int

    @Query("DELETE FROM snoozes WHERE status IN ('EXPIRED', 'DISMISSED') AND snoozeEndTime < :cutoffTime")
    suspend fun deleteOldHistory(cutoffTime: Long): Int

    @Query("DELETE FROM snoozes")
    suspend fun deleteAll()
}
