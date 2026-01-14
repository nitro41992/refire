package com.narasimha.refire.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Represents an individual message from a grouped notification.
 * Used for extracting MessagingStyle messages from notifications.
 */
@Immutable
@Serializable
data class MessageData(
    val sender: String,
    val text: String,
    val timestamp: Long
)
