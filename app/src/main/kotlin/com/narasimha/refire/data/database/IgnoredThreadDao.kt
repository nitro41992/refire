package com.narasimha.refire.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for ignored threads.
 */
@Dao
interface IgnoredThreadDao {
    /**
     * Get all ignored threads as a Flow (for Settings UI).
     */
    @Query("SELECT * FROM ignored_threads ORDER BY ignoredAt DESC")
    fun getAllIgnored(): Flow<List<IgnoredThreadEntity>>

    /**
     * Get all ignored thread IDs synchronously (for cache refresh).
     */
    @Query("SELECT threadId FROM ignored_threads")
    suspend fun getAllIgnoredThreadIds(): List<String>

    /**
     * Get count of ignored threads as a Flow.
     */
    @Query("SELECT COUNT(*) FROM ignored_threads")
    fun getIgnoredCount(): Flow<Int>

    /**
     * Insert an ignored thread (replaces if exists).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: IgnoredThreadEntity)

    /**
     * Delete an ignored thread by its threadId.
     */
    @Query("DELETE FROM ignored_threads WHERE threadId = :threadId")
    suspend fun deleteByThreadId(threadId: String)

    /**
     * Check if a thread is ignored.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM ignored_threads WHERE threadId = :threadId)")
    suspend fun isIgnored(threadId: String): Boolean

    /**
     * Get package names for all package-level ignores.
     * Used to check if a notification's package is ignored (even if threadId differs).
     */
    @Query("SELECT packageName FROM ignored_threads WHERE isPackageLevel = 1")
    suspend fun getPackageLevelIgnoredPackages(): List<String>
}
