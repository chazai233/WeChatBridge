package com.xiangming.wechatbridge.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.IntentCompat
import com.xiangming.wechatbridge.R
import com.xiangming.wechatbridge.core.DisplayName
import com.xiangming.wechatbridge.core.IncomingItem
import java.io.IOException

/**
 * Turns a share (`ACTION_SEND` / `ACTION_SEND_MULTIPLE`) or an
 * 「用其他应用打开」 (`ACTION_VIEW`) into Inbox items. Streams are opened
 * lazily by the Inbox while this activity still holds the URI grant.
 */
object IncomingIntents {
    fun read(context: Context, intent: Intent): List<IncomingItem> {
        val resolver = context.contentResolver
        val uris = mutableListOf<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris += it }
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.let { uris += it }
            Intent.ACTION_VIEW -> intent.data?.let { uris += it }
        }
        // Some senders only fill ClipData.
        if (uris.isEmpty()) {
            intent.clipData?.let { clip ->
                for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let { uris += it }
            }
        }
        val fallbackType = intent.type?.takeUnless { it.contains('*') }
        val items = uris.distinct().map { item(context, resolver, it, fallbackType) }.toMutableList()

        if (intent.action != Intent.ACTION_VIEW) {
            val texts = buildList {
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.let { add(it.toString()) }
                intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)?.forEach { add(it.toString()) }
            }.filter { it.isNotBlank() }
            if (texts.isNotEmpty()) {
                items += IncomingItem.text(context.getString(R.string.shared_text_file_name), texts.joinToString("\n\n"))
            }
        }
        return items
    }

    private fun item(context: Context, resolver: ContentResolver, uri: Uri, fallbackType: String?): IncomingItem {
        val type = runCatching { resolver.getType(uri) }.getOrNull() ?: fallbackType
        val extension = type?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        val name = displayName(resolver, uri)
            ?: (context.getString(R.string.shared_file_name) + (extension?.let { ".$it" } ?: ""))
        return IncomingItem(DisplayName.sanitize(name, extension), type) {
            resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
        }
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) return uri.lastPathSegment
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
