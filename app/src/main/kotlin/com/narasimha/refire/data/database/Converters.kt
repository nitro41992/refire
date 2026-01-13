package com.narasimha.refire.data.database

import androidx.room.TypeConverter
import com.narasimha.refire.data.model.MessageData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Room TypeConverters for storing complex types in the database.
 */
class Converters {
    @TypeConverter
    fun fromMessageList(messages: List<MessageData>?): String? {
        if (messages == null || messages.isEmpty()) return null
        return Json.encodeToString(messages)
    }

    @TypeConverter
    fun toMessageList(json: String?): List<MessageData> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            Json.decodeFromString<List<MessageData>>(json)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
