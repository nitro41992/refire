package com.narasimha.refire.data.model

/**
 * Determines the scope of an ignore action.
 */
enum class IgnoreScope {
    THREAD,  // Ignore this specific conversation/notification
    APP      // Ignore all notifications from this app
}
