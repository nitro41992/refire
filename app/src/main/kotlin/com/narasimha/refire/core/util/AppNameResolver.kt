package com.narasimha.refire.core.util

import android.content.Context
import android.content.pm.PackageManager

/**
 * Utility for resolving friendly app names from package names.
 * Shared between NotificationInfo and SnoozeRecord for consistency.
 */
object AppNameResolver {
    /**
     * Resolve friendly app name from package name using PackageManager.
     * Falls back to formatted package name if app not found.
     *
     * @param context Android context for PackageManager access
     * @param packageName The package name to resolve (e.g., "com.reddit.frontpage")
     * @return Friendly app name (e.g., "Reddit") or formatted fallback
     */
    fun getAppName(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            // Fallback: Format package name (e.g., "com.reddit.frontpage" → "Frontpage")
            packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
        }
    }
}
