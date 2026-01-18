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
     * In-memory cache of package names for package-level ignores.
     * Used to check if entire app is ignored (even when threadId differs).
     */
    @Volatile
    private var cachedIgnoredPackages: Set<String> = emptySet()

    /**
     * Check if a thread or its package is ignored (synchronous, uses cache).
     * @param threadId The thread identifier to check
     * @param packageName The package name to check for package-level ignores
     * @return true if either the thread ID matches OR the package has a package-level ignore
     */
    fun isIgnored(threadId: String, packageName: String): Boolean {
        return threadId in cachedIgnoredIds || packageName in cachedIgnoredPackages
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
        cachedIgnoredPackages = ignoredThreadDao.getPackageLevelIgnoredPackages().toSet()
        Log.d(TAG, "Refreshed cache: ${cachedIgnoredIds.size} ignored threads, ${cachedIgnoredPackages.size} package-level ignores")
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
        if (isPackageLevel) {
            cachedIgnoredPackages = cachedIgnoredPackages + packageName
        }
        Log.i(TAG, "Ignored thread: $threadId (packageLevel=$isPackageLevel)")
    }

    /**
     * Unignore a thread.
     */
    suspend fun unignoreThread(threadId: String) {
        ignoredThreadDao.deleteByThreadId(threadId)
        cachedIgnoredIds = cachedIgnoredIds - threadId
        // For package-level ignores, threadId == packageName, so remove from both caches
        cachedIgnoredPackages = cachedIgnoredPackages - threadId
        Log.i(TAG, "Unignored thread: $threadId")
    }
}
