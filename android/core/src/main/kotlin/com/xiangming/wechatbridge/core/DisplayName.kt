package com.xiangming.wechatbridge.core

/**
 * Turns a host-supplied name into something safe to write to disk, ported
 * from macOS `DisplayName`. Android adds the FAT/exFAT reserved characters
 * because SAF folders often live on an SD card or a vault synced from Windows.
 */
object DisplayName {
    const val FALLBACK_BASE_NAME = "共享文件"

    private const val FORBIDDEN = "/\\:*?\"<>|\u0000"

    fun sanitize(raw: String?, fallbackExtension: String? = null): String {
        val leaf = (raw ?: "").trimEnd('/').substringAfterLast('/').substringAfterLast('\\')
        var name = buildString {
            for (character in leaf) {
                when {
                    character in FORBIDDEN -> append('-')
                    // Newlines a host might smuggle in have no place in a filename.
                    Character.isISOControl(character) -> append(' ')
                    else -> append(character)
                }
            }
        }.trim()
        // "." and ".." are directory entries, and a leading dot hides the file.
        if (name.isEmpty() || name.startsWith(".")) {
            name = FALLBACK_BASE_NAME + name
        }
        if (extension(name).isEmpty() && !fallbackExtension.isNullOrEmpty()) {
            name = "$name.$fallbackExtension"
        }
        return truncate(name)
    }

    /**
     * Each component sanitised and rejoined with "/", so 「参考/微信流」 keeps
     * its hierarchy. Empty input yields "".
     */
    fun subfolderPath(raw: String?): String =
        (raw ?: "")
            .split('/')
            .filter { it.isNotBlank() }
            .map { sanitize(it) }
            .joinToString("/")

    fun extension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0 || dot == name.length - 1) "" else name.substring(dot + 1)
    }

    fun stem(name: String): String {
        val extension = extension(name)
        return if (extension.isEmpty()) name else name.dropLast(extension.length + 1)
    }

    /** `name.md`, then `name 2.md`, `name 3.md`… Case-insensitive, like FAT and most SAF providers. */
    fun unique(name: String, taken: Collection<String>): String {
        val lower = taken.mapTo(HashSet()) { it.lowercase() }
        if (name.lowercase() !in lower) return name
        val extension = extension(name)
        val stem = stem(name)
        return generateSequence(2) { it + 1 }
            .map { if (extension.isEmpty()) "$stem $it" else "$stem $it.$extension" }
            .first { it.lowercase() !in lower }
    }

    /** Keeps the extension attached: a truncated name without one opens nowhere. */
    fun truncate(name: String, limit: Int = 200): String {
        if (name.toByteArray(Charsets.UTF_8).size <= limit) return name
        val extension = extension(name)
        val suffix = if (extension.isEmpty()) "" else ".$extension"
        var base = stem(name)
        val budget = maxOf(1, limit - suffix.toByteArray(Charsets.UTF_8).size)
        while (base.isNotEmpty() && base.toByteArray(Charsets.UTF_8).size > budget) {
            base = base.dropLast(1)
        }
        if (base.isNotEmpty() && Character.isHighSurrogate(base.last())) base = base.dropLast(1)
        if (base.isEmpty()) base = FALLBACK_BASE_NAME
        return base + suffix
    }
}
