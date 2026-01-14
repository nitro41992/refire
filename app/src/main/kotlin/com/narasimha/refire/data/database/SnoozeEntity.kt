package com.narasimha.refire.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.narasimha.refire.data.model.SnoozeRecord
import com.narasimha.refire.data.model.SnoozeSource
import com.narasimha.refire.data.model.SnoozeStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Entity(tableName = "snoozes")
data class SnoozeEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    val notificationKey: String?,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String?,
    val snoozeEndTime: Long,           // Stored as epoch millis
    val createdAt: Long,               // Stored as epoch millis
    @ColumnInfo(name = "source_type")
    val source: String,                // "NOTIFICATION" or "SHARE_SHEET"
    val shortcutId: String?,           // For deep-linking
    val groupKey: String?,             // Fallback for deep-linking
    val contentType: String?,          // "URL", "PLAIN_TEXT", "IMAGE", null for notifications
    val messagesJson: String? = null,  // Serialized List<MessageData> as JSON
    @ColumnInfo(name = "status", defaultValue = "ACTIVE")
    val status: String = "ACTIVE",     // "ACTIVE" or "EXPIRED"
    @ColumnInfo(name = "suppressedCount", defaultValue = "0")
    val suppressedCount: Int = 0       // Count of messages suppressed after snooze creation
)

/**
 * Convert Room entity to domain model.
 */
fun SnoozeEntity.toSnoozeRecord(): SnoozeRecord {
    return SnoozeRecord(
        id = id,
        threadId = threadId,
        notificationKey = notificationKey,
        packageName = packageName,
        appName = appName,
        title = title,
        text = text,
        snoozeEndTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(snoozeEndTime),
            ZoneId.systemDefault()
        ),
        createdAt = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(createdAt),
            ZoneId.systemDefault()
        ),
        source = SnoozeSource.valueOf(source),
        shortcutId = shortcutId,
        groupKey = groupKey,
        contentType = contentType,
        messages = messagesJson?.let {
            try {
                Json.decodeFromString(it)
            } catch (e: Exception) {
                emptyList()
            }
        } ?: emptyList(),
        status = try {
            SnoozeStatus.valueOf(status)
        } catch (e: Exception) {
            SnoozeStatus.ACTIVE
        },
        suppressedCount = suppressedCount
    )
}

/**
 * Convert domain model to Room entity.
 */
fun SnoozeRecord.toEntity(): SnoozeEntity {
    return SnoozeEntity(
        id = id,
        threadId = threadId,
        notificationKey = notificationKey,
        packageName = packageName,
        appName = appName,
        title = title,
        text = text,
        snoozeEndTime = snoozeEndTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli(),
        createdAt = createdAt
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli(),
        source = source.name,
        shortcutId = shortcutId,
        groupKey = groupKey,
        contentType = contentType,
        messagesJson = if (messages.isEmpty()) null
            else Json.encodeToString(messages),
        status = status.name,
        suppressedCount = suppressedCount
    )
}
