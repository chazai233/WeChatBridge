package com.xiangming.wechatbridge.data

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

/** Small user choices. Chat content never goes in here. */
class Preferences(context: Context) {
    private val store = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var folderTree: Uri?
        get() = store.getString(KEY_FOLDER, null)?.let(Uri::parse)
        set(value) = store.edit { putString(KEY_FOLDER, value?.toString()) }

    var obsidianTree: Uri?
        get() = store.getString(KEY_OBSIDIAN, null)?.let(Uri::parse)
        set(value) = store.edit { putString(KEY_OBSIDIAN, value?.toString()) }

    /** Empty means the default 「微信流」 subfolder. */
    var subfolder: String
        get() = store.getString(KEY_SUBFOLDER, "").orEmpty()
        set(value) = store.edit { putString(KEY_SUBFOLDER, value) }

    var includeAttachments: Boolean
        get() = store.getBoolean(KEY_ATTACHMENTS, true)
        set(value) = store.edit { putBoolean(KEY_ATTACHMENTS, value) }

    var lastTargetId: String?
        get() = store.getString(KEY_LAST_TARGET, null)
        set(value) = store.edit { putString(KEY_LAST_TARGET, value) }

    /** `null` means never chosen; [NO_SCENE] means the user picked 「不附加」 last time. */
    var lastSceneId: String?
        get() = store.getString(KEY_LAST_SCENE, null)
        set(value) = store.edit { putString(KEY_LAST_SCENE, value) }

    companion object {
        const val NO_SCENE = ""
        private const val KEY_FOLDER = "folderTree"
        private const val KEY_OBSIDIAN = "obsidianTree"
        private const val KEY_SUBFOLDER = "subfolder"
        private const val KEY_ATTACHMENTS = "includeAttachments"
        private const val KEY_LAST_TARGET = "lastTargetId"
        private const val KEY_LAST_SCENE = "lastSceneId"
    }
}
