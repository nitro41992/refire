package com.narasimha.refire.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for storing ignored threads in the database.
 * Ignored threads won't be tracked in the app (Active, Dismissed, History views)
 * and won't count toward helper notification threshold.
 */
@Entity(tableName = "ignored_threads")
data class IgnoredThreadEntity(
    @PrimaryKey val threadId: String,
    val packageName: String,
    val appName: String,
    val displayTitle: String,
    val ignoredAt: Long,
    val isPackageLevel: Boolean = false  // True when fallback to package-level (no shortcutId/groupKey)
)
