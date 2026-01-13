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

    @Query("SELECT * FROM snoozes WHERE snoozeEndTime > :currentTime ORDER BY snoozeEndTime ASC")
    fun getActiveSnoozes(currentTime: Long): Flow<List<SnoozeEntity>>

    @Query("SELECT * FROM snoozes WHERE id = :id")
    suspend fun getSnoozeById(id: String): SnoozeEntity?

    @Query("SELECT * FROM snoozes WHERE threadId = :threadId LIMIT 1")
    suspend fun getSnoozeByThread(threadId: String): SnoozeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnooze(snooze: SnoozeEntity)

    @Query("DELETE FROM snoozes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM snoozes WHERE threadId = :threadId")
    suspend fun deleteByThread(threadId: String)

    @Query("DELETE FROM snoozes WHERE snoozeEndTime <= :currentTime")
    suspend fun deleteExpired(currentTime: Long): Int

    @Query("DELETE FROM snoozes")
    suspend fun deleteAll()
}
