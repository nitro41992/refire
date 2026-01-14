package com.narasimha.refire.data.model

import java.time.ZoneId

/**
 * Sealed class representing items in the LIVE tab.
 * Enables unified sorting of active notifications and dismissed records by timestamp.
 */
sealed class LiveItem {
    abstract val timestamp: Long
    abstract val id: String

    data class Active(val notification: NotificationInfo) : LiveItem() {
        override val timestamp: Long get() = notification.postTime
        override val id: String get() = "active_${notification.getThreadIdentifier()}"
    }

    data class Dismissed(val record: SnoozeRecord) : LiveItem() {
        override val timestamp: Long get() = record.createdAt
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        override val id: String get() = "dismissed_${record.id}"
    }
}
