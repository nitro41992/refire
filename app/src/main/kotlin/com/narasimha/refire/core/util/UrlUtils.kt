package com.narasimha.refire.core.util

import android.net.Uri

/**
 * Utility for URL parsing and formatting.
 */
object UrlUtils {
    /**
     * Extract domain from URL for display.
     * Examples:
     *   "https://www.reddit.com/r/foo" → "reddit.com"
     *   "https://twitter.com/user/status/123" → "twitter.com"
     *   "http://example.co.uk/path" → "example.co.uk"
     */
    fun extractDomain(url: String): String? {
        return try {
            val uri = Uri.parse(url)
            uri.host?.removePrefix("www.")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Truncate URL intelligently for display.
     * Shows domain + first part of path.
     */
    fun truncateUrl(url: String, maxLength: Int = 50): String {
        return if (url.length <= maxLength) {
            url
        } else {
            url.take(maxLength) + "..."
        }
    }
}
