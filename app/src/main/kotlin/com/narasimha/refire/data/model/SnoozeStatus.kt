package com.narasimha.refire.data.model

/**
 * Represents the lifecycle state of a snooze record.
 */
enum class SnoozeStatus {
    ACTIVE,     // Currently snoozed, waiting to re-fire
    EXPIRED     // Re-fired, now in history
}
