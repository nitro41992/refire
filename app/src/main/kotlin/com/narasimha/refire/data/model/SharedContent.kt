package com.narasimha.refire.data.model

import android.content.Intent
import android.net.Uri

/**
 * Represents content shared via Share Sheet (ACTION_SEND).
 */
data class SharedContent(
    val type: ContentType,
    val text: String?,
    val uri: Uri?,
    val sourcePackage: String?,
    val subject: String?,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class ContentType {
        URL,
        PLAIN_TEXT,
        IMAGE,
        UNKNOWN
    }

    companion object {
        private val URL_PATTERN = Regex("""https?://[^\s]+""")

        fun fromIntent(intent: Intent): SharedContent? {
            if (intent.action != Intent.ACTION_SEND) return null

            val mimeType = intent.type ?: return null
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            @Suppress("DEPRECATION")
            val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            val sourcePackage = intent.`package`

            val contentType = when {
                text?.startsWith("http://") == true ||
                text?.startsWith("https://") == true -> ContentType.URL
                mimeType.startsWith("image/") -> ContentType.IMAGE
                mimeType == "text/plain" -> ContentType.PLAIN_TEXT
                else -> ContentType.UNKNOWN
            }

            return SharedContent(
                type = contentType,
                text = text,
                uri = stream,
                sourcePackage = sourcePackage,
                subject = subject
            )
        }
    }

    /**
     * Extracts URL if present in text content.
     */
    fun extractUrl(): String? {
        if (type == ContentType.URL) return text
        return text?.let { URL_PATTERN.find(it)?.value }
    }

    /**
     * Returns display title for the content.
     */
    fun getDisplayTitle(): String {
        return subject ?: when (type) {
            ContentType.URL -> extractUrl()?.take(50) ?: "Shared URL"
            ContentType.PLAIN_TEXT -> text?.take(50) ?: "Shared Text"
            ContentType.IMAGE -> "Shared Image"
            ContentType.UNKNOWN -> "Shared Content"
        }
    }
}
