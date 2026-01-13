package com.narasimha.refire.data.model

/**
 * Represents the lifecycle state of a snooze record.
 */
enum class SnoozeStatus {
    ACTIVE,     // Currently scheduled, waiting to re-fire
    EXPIRED,    // Re-fired, now in history
    DISMISSED   // Notification was dismissed (not scheduled), in history
}
