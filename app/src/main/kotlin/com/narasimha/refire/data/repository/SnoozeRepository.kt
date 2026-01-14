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
import android.util.Log
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
     * Only deletes ACTIVE snoozes for the same thread - history records are preserved.
     */
    suspend fun insertSnooze(record: SnoozeRecord) {
        // Atomically replace any existing active snooze for this thread
        // This preserves history records (EXPIRED/DISMISSED) for the same thread
        snoozeDao.replaceActiveSnoozeForThread(record.threadId, record.toEntity())
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
     * Flow of history snoozes (expired/fired snoozes only).
     */
    val historySnoozes: Flow<List<SnoozeRecord>> =
        snoozeDao.getHistorySnoozes()
            .map { entities -> entities.map { it.toSnoozeRecord() } }

    /**
     * Flow of dismissed notifications (for LIVE tab display).
     */
    val dismissedSnoozes: Flow<List<SnoozeRecord>> =
        snoozeDao.getDismissedSnoozes()
            .map { entities -> entities.map { it.toSnoozeRecord() } }

    /**
     * Mark a snooze as expired (moved to history).
     */
    suspend fun markAsExpired(snoozeId: String) {
        snoozeDao.updateStatus(snoozeId, SnoozeStatus.EXPIRED.name)
    }

    /**
     * Mark a snooze as dismissed (moved to history).
     * Updates snoozeEndTime to now so it sorts correctly in history.
     */
    suspend fun markAsDismissed(snoozeId: String) {
        val now = java.time.LocalDateTime.now()
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        snoozeDao.updateStatusAndEndTime(snoozeId, SnoozeStatus.DISMISSED.name, now)
    }

    /**
     * Get all EXPIRED history entries for a thread.
     * Note: DISMISSED records are separate (shown in LIVE tab, not History).
     */
    suspend fun getHistoryByThread(threadId: String): List<SnoozeRecord> {
        return snoozeDao.getHistoryByThread(threadId).map { it.toSnoozeRecord() }
    }

    /**
     * Delete all EXPIRED history entries for a thread.
     * Note: DISMISSED records are separate and must be deleted via deleteByThreadAndStatus.
     */
    suspend fun deleteHistoryByThread(threadId: String) {
        snoozeDao.deleteHistoryByThread(threadId)
    }

    /**
     * Insert a dismissed notification into history, replacing any existing entry for the same thread+status.
     * Uses atomic transaction to prevent duplicate records from race conditions.
     * DISMISSED records are separate from EXPIRED records (same thread can have both).
     */
    suspend fun insertOrMergeHistory(record: SnoozeRecord) {
        // Atomic replace: deletes any existing record with same threadId+status, then inserts new one
        // This prevents duplicates even when multiple coroutines run concurrently
        snoozeDao.replaceForThreadAndStatus(record.toEntity())
        Log.d("SnoozeRepository", "Replaced/inserted ${record.status} entry: ${record.threadId}")
    }

    /**
     * Merge messages from multiple history records into one list.
     * Deduplicates by timestamp, sorts by most recent, keeps 20.
     */
    fun mergeHistoryMessages(records: List<SnoozeRecord>): List<MessageData> {
        return records
            .flatMap { it.messages }
            .distinctBy { it.timestamp }
            .sortedByDescending { it.timestamp }
            .take(20)
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
     * Deduplicates by timestamp to prevent double-counting.
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

        // DEDUPLICATION: Filter out messages we already have (by timestamp)
        // This prevents double-counting when the same notification is posted multiple times
        val existingTimestamps = existingMessages.map { it.timestamp }.toSet()
        val trulyNewMessages = newMessages.filter { it.timestamp !in existingTimestamps }

        // If all messages are duplicates, nothing to do
        if (trulyNewMessages.isEmpty()) return

        // Merge messages, deduplicate by timestamp, sort by most recent, keep 20
        val merged = (existingMessages + trulyNewMessages)
            .distinctBy { it.timestamp }
            .sortedByDescending { it.timestamp }
            .take(20)

        // Only count truly new messages
        val newSuppressedCount = existingRecord.suppressedCount + trulyNewMessages.size

        val messagesJson = if (merged.isEmpty()) null else Json.encodeToString(merged)
        // Keep existing snoozeEndTime unchanged for active snoozes
        val existingEndTime = existingRecord.snoozeEndTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        snoozeDao.updateMessagesAndSuppressedCount(snoozeId, messagesJson, newSuppressedCount, existingEndTime)
    }
}
