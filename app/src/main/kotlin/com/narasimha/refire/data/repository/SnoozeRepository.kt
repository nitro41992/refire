package com.narasimha.refire.data.repository

import com.narasimha.refire.data.database.SnoozeDao
import com.narasimha.refire.data.database.toEntity
import com.narasimha.refire.data.database.toSnoozeRecord
import com.narasimha.refire.data.model.MessageData
import com.narasimha.refire.data.model.SnoozeRecord
import com.narasimha.refire.data.model.SnoozeStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

    /**
     * Flow of history snoozes (expired scheduled items + dismissed notifications).
     */
    val historySnoozes: Flow<List<SnoozeRecord>> =
        snoozeDao.getHistorySnoozes()
            .map { entities -> entities.map { it.toSnoozeRecord() } }

    /**
     * Mark a snooze as expired (moved to history).
     */
    suspend fun markAsExpired(snoozeId: String) {
        snoozeDao.updateStatus(snoozeId, SnoozeStatus.EXPIRED.name)
    }

    /**
     * Mark a snooze as dismissed (moved to history).
     */
    suspend fun markAsDismissed(snoozeId: String) {
        snoozeDao.updateStatus(snoozeId, SnoozeStatus.DISMISSED.name)
    }

    /**
     * Clean up history entries older than 7 days.
     */
    suspend fun cleanupOldHistory() {
        val cutoff = LocalDateTime.now().minusDays(7)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        snoozeDao.deleteOldHistory(cutoff)
    }

    /**
     * Append suppressed messages to an existing snooze.
     * Keeps only the 20 most recent messages and tracks suppressed count.
     */
    suspend fun appendSuppressedMessages(snoozeId: String, newMessages: List<MessageData>) {
        val existing = snoozeDao.getSnoozeById(snoozeId) ?: return
        val existingRecord = existing.toSnoozeRecord()

        // Get existing messages, or create one from text if messages is empty
        val existingMessages = if (existingRecord.messages.isNotEmpty()) {
            existingRecord.messages
        } else if (!existingRecord.text.isNullOrBlank()) {
            // Create synthetic message from original notification text
            val timestamp = existingRecord.createdAt
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            listOf(MessageData(sender = "", text = existingRecord.text, timestamp = timestamp))
        } else {
            emptyList()
        }

        // Merge messages, keeping 20 most recent
        val merged = (existingMessages + newMessages).takeLast(20)
        val newSuppressedCount = existingRecord.suppressedCount + newMessages.size

        val messagesJson = if (merged.isEmpty()) null else Json.encodeToString(merged)
        snoozeDao.updateMessagesAndSuppressedCount(snoozeId, messagesJson, newSuppressedCount)
    }
}
