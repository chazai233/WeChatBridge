package com.xiangming.wechatbridge.deliver

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.xiangming.wechatbridge.R
import com.xiangming.wechatbridge.data.Bridge
import com.xiangming.wechatbridge.data.userError
import com.xiangming.wechatbridge.core.DeliveryPlan
import com.xiangming.wechatbridge.core.DeliveryPlanner
import com.xiangming.wechatbridge.core.DeliveryTarget
import com.xiangming.wechatbridge.core.KnowledgeDelivery
import com.xiangming.wechatbridge.core.Lang
import com.xiangming.wechatbridge.core.Material
import com.xiangming.wechatbridge.core.MediaKind
import com.xiangming.wechatbridge.core.OutcomeKind
import com.xiangming.wechatbridge.core.Scene
import com.xiangming.wechatbridge.core.ScenePrompt
import com.xiangming.wechatbridge.core.TargetKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.util.UUID

data class SendRequest(
    val batchIds: List<String>,
    val collectionId: String?,
    val target: DeliveryTarget,
    val scene: Scene?,
    val chatName: String?,
    val includeAttachments: Boolean,
)

data class SendOutcome(
    val ok: Boolean,
    val message: String,
    /** Something to offer afterwards, e.g. opening the note in Obsidian. */
    val followUp: Intent? = null,
)

/**
 * Carries out one send. Android has no auto-paste, so an AI app gets an
 * `ACTION_SEND` addressed to its package with the scene prompt and chat as
 * `EXTRA_TEXT` and the media as `EXTRA_STREAM`. The same text goes to the
 * clipboard first: if the app is missing, refuses the share or drops the text,
 * the user is one long-press paste away from done.
 */
class Deliverer(private val activity: Activity, private val bridge: Bridge) {
    private val lang = bridge.lang()

    private data class Attempt(
        val kind: OutcomeKind,
        val detail: String,
        val message: String = detail,
        val followUp: Intent? = null,
    )

    suspend fun deliver(request: SendRequest, material: Material): SendOutcome {
        val target = request.target
        val targetName = target.displayName(lang)
        if (request.collectionId != null) {
            try {
                withContext(Dispatchers.IO) { bridge.updateLedger { it.beginDelivery(request.collectionId).first } }
            } catch (error: Exception) {
                return SendOutcome(false, activity.getString(R.string.collection_busy))
            }
        }
        val prompt = request.scene?.let { ScenePrompt.render(it, lang) }
        val attempt = try {
            when (target.kind) {
                TargetKind.CLIPBOARD -> copy(material, prompt)
                TargetKind.AI_APP -> sendToApp(target, material, prompt, request.includeAttachments)
                TargetKind.SYSTEM_SHARE -> shareSheet(material, prompt, request.includeAttachments)
                TargetKind.FOLDER, TargetKind.OBSIDIAN -> withContext(Dispatchers.IO) { saveNote(target, material, request) }
            }
        } catch (error: Exception) {
            val reason = activity.userError(error)
            Attempt(OutcomeKind.FAILED, activity.getString(R.string.detail_failed, reason))
        }
        withContext(Dispatchers.IO) {
            bridge.recordOutcome(request.batchIds, attempt.kind, attempt.detail, target, request.scene, request.chatName)
            request.collectionId?.let { id ->
                runCatching {
                    bridge.updateLedger {
                        it.finishDelivery(
                            id,
                            succeeded = attempt.kind != OutcomeKind.FAILED,
                            target = targetName,
                            detail = attempt.detail,
                            scene = request.scene?.name,
                        )
                    }
                }
            }
            bridge.preferences.lastTargetId = target.id
        }
        return SendOutcome(attempt.kind != OutcomeKind.FAILED, attempt.message, attempt.followUp)
    }

    private fun copy(material: Material, prompt: String?): Attempt {
        val text = DeliveryPlanner.compose(prompt, material.chatText, lang)
        return if (copyToClipboard(text)) {
            Attempt(OutcomeKind.COPIED, activity.getString(R.string.detail_copied))
        } else {
            Attempt(OutcomeKind.FAILED, activity.getString(R.string.detail_copy_failed))
        }
    }

    private suspend fun sendToApp(
        target: DeliveryTarget,
        material: Material,
        prompt: String?,
        includeAttachments: Boolean,
    ): Attempt {
        val name = target.displayName(lang)
        val packageName = requireNotNull(target.packageName)
        val plan = DeliveryPlanner.plan(material, prompt, includeAttachments, lang)
        val files = withContext(Dispatchers.IO) { filesFor(plan) }
        copyToClipboard(plan.clipboardText)

        if (!isInstalled(packageName)) {
            return Attempt(
                OutcomeKind.FAILED,
                activity.getString(R.string.detail_not_installed, name),
            )
        }
        if (start(sendIntent(plan.text, files).setPackage(packageName))) {
            return Attempt(
                OutcomeKind.DELIVERED,
                activity.getString(R.string.detail_handed_to, name),
                activity.getString(R.string.toast_handed_to, name),
            )
        }
        // The app takes text but not this mix of files.
        if (files.isNotEmpty() && start(sendIntent(plan.text, emptyList()).setPackage(packageName))) {
            return Attempt(
                OutcomeKind.DELIVERED,
                activity.getString(R.string.detail_handed_text_only, name),
                activity.getString(R.string.toast_handed_text_only, name),
            )
        }
        // The app takes no shares at all: open it and let the clipboard carry the text.
        val launch = activity.packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null && start(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
            return Attempt(
                OutcomeKind.DELIVERED,
                activity.getString(R.string.detail_opened_app, name),
                activity.getString(R.string.toast_opened_app, name),
            )
        }
        return Attempt(OutcomeKind.FAILED, activity.getString(R.string.detail_not_installed, name))
    }

    private suspend fun shareSheet(material: Material, prompt: String?, includeAttachments: Boolean): Attempt {
        val plan = DeliveryPlanner.plan(material, prompt, includeAttachments, lang)
        val files = withContext(Dispatchers.IO) { filesFor(plan) }
        copyToClipboard(plan.clipboardText)
        val chooser = Intent.createChooser(sendIntent(plan.text, files), activity.getString(R.string.share_sheet_title))
        return if (start(chooser)) {
            Attempt(OutcomeKind.DELIVERED, activity.getString(R.string.detail_share_sheet))
        } else {
            Attempt(OutcomeKind.FAILED, activity.getString(R.string.detail_copy_only))
        }
    }

    private fun saveNote(target: DeliveryTarget, material: Material, request: SendRequest): Attempt {
        val preferences = bridge.preferences
        val tree = (if (target.kind == TargetKind.OBSIDIAN) preferences.obsidianTree else preferences.folderTree)
            ?: return Attempt(OutcomeKind.FAILED, activity.getString(R.string.detail_folder_missing))
        val root = DocumentFile.fromTreeUri(activity, tree)
        if (root == null || !root.canWrite()) {
            return Attempt(OutcomeKind.FAILED, activity.getString(R.string.detail_folder_lost))
        }
        val result = KnowledgeDelivery.deliver(
            root = DocumentNoteFolder(activity.contentResolver, root),
            subfolder = preferences.subfolder,
            material = material,
            chatName = request.chatName,
            sceneName = request.scene?.name,
            now = LocalDateTime.now(),
        )
        val shown = "${root.name.orEmpty()}/${result.notePath}"
        val detail = if (result.unchanged) {
            activity.getString(R.string.detail_saved_unchanged, shown)
        } else {
            activity.getString(R.string.detail_saved, shown)
        }
        val followUp = if (target.kind == TargetKind.OBSIDIAN) {
            val file = result.notePath.removeSuffix(".md")
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("obsidian://open?vault=${Uri.encode(root.name.orEmpty())}&file=${Uri.encode(file)}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            null
        }
        return Attempt(OutcomeKind.DELIVERED, detail, followUp = followUp)
    }

    /** Attachments, plus the transcript as a TXT when it is too long for an intent extra. */
    private fun filesFor(plan: DeliveryPlan): List<Pair<File, String>> {
        val files = plan.attachments.map { it.file to it.originalName }.toMutableList()
        val transcript = plan.transcriptFileText ?: return files
        val directory = File(activity.cacheDir, "work/${UUID.randomUUID()}").apply { mkdirs() }
        val file = File(directory, DeliveryPlanner.TRANSCRIPT_FILE_NAME)
        file.writeText(transcript)
        files.add(0, file to file.name)
        return files
    }

    private fun sendIntent(text: String, files: List<Pair<File, String>>): Intent {
        val authority = "${activity.packageName}.files"
        val uris = files.map { FileProvider.getUriForFile(activity, authority, it.first) }
        val names = files.map { it.second }
        val intent = when (uris.size) {
            0 -> Intent(Intent.ACTION_SEND).setType("text/plain")
            1 -> Intent(Intent.ACTION_SEND)
                .setType(MediaKind.mimeType(names[0]))
                .putExtra(Intent.EXTRA_STREAM, uris[0])
            else -> Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType(MediaKind.commonMimeType(names))
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        if (text.isNotEmpty()) intent.putExtra(Intent.EXTRA_TEXT, text)
        if (uris.isNotEmpty()) {
            val clip = ClipData.newRawUri(null, uris[0])
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            intent.clipData = clip
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun start(intent: Intent): Boolean = try {
        activity.startActivity(intent)
        true
    } catch (ignored: ActivityNotFoundException) {
        false
    } catch (ignored: SecurityException) {
        false
    }

    private fun isInstalled(packageName: String): Boolean = try {
        @Suppress("DEPRECATION")
        activity.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (ignored: PackageManager.NameNotFoundException) {
        false
    }

    private fun copyToClipboard(text: String): Boolean = try {
        val clipboard = activity.getSystemService(ClipboardManager::class.java) ?: throw IllegalStateException()
        clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.app_name), text))
        true
    } catch (ignored: RuntimeException) {
        false
    }
}
