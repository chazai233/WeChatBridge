package com.xiangming.wechatbridge.core

/** What a shared or unpacked file is, decided by extension first, then MIME type. */
enum class MediaKind {
    IMAGE, VIDEO, AUDIO, PDF, TEXT, ZIP, OTHER;

    /** Obsidian renders these inline with `![[…]]`; anything else is a plain link. */
    val embeds: Boolean get() = this == IMAGE || this == VIDEO || this == AUDIO || this == PDF

    companion object {
        private val images = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp")
        private val videos = setOf("mp4", "mov", "m4v", "3gp", "webm", "mkv", "avi")
        private val audio = setOf("mp3", "m4a", "aac", "amr", "wav", "ogg", "silk")

        private val mimeByExtension = mapOf(
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "png" to "image/png",
            "gif" to "image/gif",
            "webp" to "image/webp",
            "heic" to "image/heic",
            "heif" to "image/heif",
            "bmp" to "image/bmp",
            "mp4" to "video/mp4",
            "m4v" to "video/mp4",
            "mov" to "video/quicktime",
            "3gp" to "video/3gpp",
            "webm" to "video/webm",
            "mkv" to "video/x-matroska",
            "avi" to "video/x-msvideo",
            "mp3" to "audio/mpeg",
            "m4a" to "audio/mp4",
            "aac" to "audio/aac",
            "amr" to "audio/amr",
            "wav" to "audio/wav",
            "ogg" to "audio/ogg",
            "pdf" to "application/pdf",
            "txt" to "text/plain",
            "md" to "text/markdown",
            "zip" to "application/zip",
        )

        fun of(name: String, mimeType: String? = null): MediaKind {
            when (DisplayName.extension(name).lowercase()) {
                in images -> return IMAGE
                in videos -> return VIDEO
                in audio -> return AUDIO
                "pdf" -> return PDF
                "txt" -> return TEXT
                "zip" -> return ZIP
            }
            val mime = mimeType?.lowercase() ?: return OTHER
            return when {
                mime.startsWith("image/") -> IMAGE
                mime.startsWith("video/") -> VIDEO
                mime.startsWith("audio/") -> AUDIO
                mime == "application/pdf" -> PDF
                mime == "text/plain" -> TEXT
                mime == "application/zip" || mime == "application/x-zip-compressed" -> ZIP
                else -> OTHER
            }
        }

        fun mimeType(name: String): String =
            mimeByExtension[DisplayName.extension(name).lowercase()] ?: "application/octet-stream"

        /** One type for a share of several files: the narrowest family that covers them all. */
        fun commonMimeType(names: List<String>): String {
            val types = names.map { mimeType(it) }.distinct()
            if (types.size == 1) return types[0]
            val families = types.map { it.substringBefore('/') }.distinct()
            return if (families.size == 1) "${families[0]}/*" else "*/*"
        }
    }
}
