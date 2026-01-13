package com.narasimha.refire.data.repository

import com.narasimha.refire.data.database.SnoozeDao
import com.narasimha.refire.data.database.toEntity
import com.narasimha.refire.data.database.toSnoozeRecord
import com.narasimha.refire.data.model.SnoozeRecord
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Repository for managing snooze records in Room database.
 */
class SnoozeRepository(private val snoozeDao: SnoozeDao) {

    /**
     * Flow of active (non-expired) snoozes.
     */
    val activeSnoozes: Flow<List<SnoozeRecord>> =
        snoozeDao.getActiveSnoozes(System.currentTimeMillis())
            .map { entities -> entities.map { it.toSnoozeRecord() } }

    /**
     * Insert or update a snooze.
     * Automatically deduplicates by threadId (latest snooze wins).
     */
    suspend fun insertSnooze(record: SnoozeRecord) {
        // Delete existing snooze for same thread (deduplication)
        snoozeDao.deleteByThread(record.threadId)
        snoozeDao.insertSnooze(record.toEntity())
    }

    /**
     * Delete a snooze by ID.
     */
    suspend fun deleteSnooze(snoozeId: String) {
        snoozeDao.deleteById(snoozeId)
    }

    /**
     * Get a snooze by ID.
     */
    suspend fun getSnoozeById(snoozeId: String): SnoozeRecord? {
        return snoozeDao.getSnoozeById(snoozeId)?.toSnoozeRecord()
    }

    /**
     * Update the end time of a snooze.
     */
    suspend fun updateSnoozeEndTime(snoozeId: String, newEndTime: LocalDateTime) {
        val entity = snoozeDao.getSnoozeById(snoozeId) ?: return
        val updated = entity.copy(
            snoozeEndTime = newEndTime
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )
        snoozeDao.insertSnooze(updated)
    }

    /**
     * Remove all expired snoozes from database.
     * Returns number of records deleted.
     */
    suspend fun cleanupExpired(): Int {
        return snoozeDao.deleteExpired(System.currentTimeMillis())
    }

    /**
     * Get all snoozes (including expired).
     */
    suspend fun getAllSnoozes(): List<SnoozeRecord> {
        return snoozeDao.getAllSnoozes().first().map { it.toSnoozeRecord() }
    }
}
