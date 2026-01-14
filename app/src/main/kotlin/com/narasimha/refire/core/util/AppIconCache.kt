package com.narasimha.refire.core.util

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/**
 * LRU cache for app icons to avoid repeated PackageManager lookups.
 * Caches up to 50 app icons (covers typical notification sources).
 */
object AppIconCache {
    private val cache = LruCache<String, ImageBitmap>(50)

    /**
     * Get app icon for the given package name.
     * Returns cached icon if available, otherwise loads from PackageManager and caches.
     */
    fun getAppIcon(context: Context, packageName: String): ImageBitmap? {
        // Check cache first
        cache.get(packageName)?.let { return it }

        // Load from PackageManager
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bitmap = (drawable as? BitmapDrawable)?.bitmap?.asImageBitmap()
                ?: drawable.toBitmap().asImageBitmap()
            cache.put(packageName, bitmap)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Clear the cache. Useful for testing or when memory pressure is high.
     */
    fun clear() {
        cache.evictAll()
    }
}
