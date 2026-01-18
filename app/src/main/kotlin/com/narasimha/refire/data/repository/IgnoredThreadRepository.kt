package com.narasimha.refire.data.repository

import android.util.Log
import com.narasimha.refire.data.database.IgnoredThreadDao
import com.narasimha.refire.data.database.IgnoredThreadEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing ignored threads.
 * Maintains an in-memory cache for fast synchronous lookups.
 */
class IgnoredThreadRepository(private val ignoredThreadDao: IgnoredThreadDao) {

    companion object {
        private const val TAG = "IgnoredThreadRepo"
    }

    /**
     * Flow of all ignored threads (for Settings UI).
     */
    val ignoredThreads: Flow<List<IgnoredThreadEntity>> = ignoredThreadDao.getAllIgnored()

    /**
     * Flow of ignored thread count (for Settings badge).
     */
    val ignoredCount: Flow<Int> = ignoredThreadDao.getIgnoredCount()

    /**
     * In-memory cache of ignored thread IDs for fast sync lookups.
     * Must be refreshed on service start.
     */
    @Volatile
    private var cachedIgnoredIds: Set<String> = emptySet()

    /**
     * Check if a thread is ignored (synchronous, uses cache).
     */
    fun isIgnored(threadId: String): Boolean {
        return threadId in cachedIgnoredIds
    }

    /**
     * Get a copy of the current ignored IDs set.
     */
    fun getIgnoredIds(): Set<String> = cachedIgnoredIds

    /**
     * Refresh the in-memory cache from database.
     * Call this on service start.
     */
    suspend fun refreshCache() {
        cachedIgnoredIds = ignoredThreadDao.getAllIgnoredThreadIds().toSet()
        Log.d(TAG, "Refreshed cache: ${cachedIgnoredIds.size} ignored threads")
    }

    /**
     * Ignore a thread.
     */
    suspend fun ignoreThread(
        threadId: String,
        packageName: String,
        appName: String,
        displayTitle: String,
        isPackageLevel: Boolean
    ) {
        val entity = IgnoredThreadEntity(
            threadId = threadId,
            packageName = packageName,
            appName = appName,
            displayTitle = displayTitle,
            ignoredAt = System.currentTimeMillis(),
            isPackageLevel = isPackageLevel
        )
        ignoredThreadDao.insert(entity)
        cachedIgnoredIds = cachedIgnoredIds + threadId
        Log.i(TAG, "Ignored thread: $threadId (packageLevel=$isPackageLevel)")
    }

    /**
     * Unignore a thread.
     */
    suspend fun unignoreThread(threadId: String) {
        ignoredThreadDao.deleteByThreadId(threadId)
        cachedIgnoredIds = cachedIgnoredIds - threadId
        Log.i(TAG, "Unignored thread: $threadId")
    }
}
