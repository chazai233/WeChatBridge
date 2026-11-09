@file:OptIn(ExperimentalMaterial3Api::class)

package com.xiangming.wechatbridge.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiangming.wechatbridge.R
import com.xiangming.wechatbridge.bridge
import com.xiangming.wechatbridge.core.DeliveryTarget
import com.xiangming.wechatbridge.core.Material
import com.xiangming.wechatbridge.core.SceneLibrary
import com.xiangming.wechatbridge.core.ScenePrompt
import com.xiangming.wechatbridge.core.TargetCatalog
import com.xiangming.wechatbridge.core.TargetKind
import com.xiangming.wechatbridge.data.Preferences
import com.xiangming.wechatbridge.data.PreparedSend
import com.xiangming.wechatbridge.data.SendSubject
import com.xiangming.wechatbridge.deliver.Deliverer
import com.xiangming.wechatbridge.deliver.SendOutcome
import com.xiangming.wechatbridge.deliver.SendRequest
import com.xiangming.wechatbridge.ui.theme.Warning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Loaded {
    data object Loading : Loaded
    data class Ready(val prepared: PreparedSend) : Loaded
    data class Failed(val message: String) : Loaded
}

/** Pick a scene and a destination for one batch or a whole collection, then send. */
@Composable
fun SendScreen(subject: SendSubject, onClose: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val bridge = context.bridge
    val loaded by produceState<Loaded>(Loaded.Loading, subject) {
        value = withContext(Dispatchers.IO) {
            try {
                Loaded.Ready(bridge.prepare(subject))
            } catch (error: Exception) {
                Loaded.Failed(error.localizedMessage ?: error.toString())
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (subject is SendSubject.Collection) R.string.send_title_collection else R.string.send_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val state = loaded) {
                Loaded.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is Loaded.Failed -> Text(
                    state.message,
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.error,
                )
                is Loaded.Ready -> SendForm(state.prepared, onDone)
            }
        }
    }
}

private fun initialSceneId(last: String?, library: SceneLibrary): String? = when {
    last == Preferences.NO_SCENE -> null
    last != null && library.scene(last) != null -> last
    else -> library.defaultSceneId
}

@Composable
private fun SendForm(prepared: PreparedSend, onDone: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val bridge = context.bridge
    val preferences = bridge.preferences
    val lang = currentLang()
    val scope = rememberCoroutineScope()
    val material = prepared.material
    val library = prepared.scenes

    var chatName by remember { mutableStateOf(prepared.suggestedChatName) }
    var sceneId by remember { mutableStateOf(initialSceneId(preferences.lastSceneId, library)) }
    var includeAttachments by remember { mutableStateOf(preferences.includeAttachments) }
    var busy by remember { mutableStateOf(false) }
    var savedNote by remember { mutableStateOf<SendOutcome?>(null) }
    var pendingTarget by remember { mutableStateOf<DeliveryTarget?>(null) }

    fun send(target: DeliveryTarget) {
        busy = true
        val scene = library.scene(sceneId)
        preferences.lastSceneId = sceneId ?: Preferences.NO_SCENE
        val request = SendRequest(
            batchIds = prepared.batchIds,
            collectionId = prepared.collectionId,
            target = target,
            scene = scene,
            chatName = chatName.trim().ifEmpty { null },
            includeAttachments = includeAttachments && material.attachments.isNotEmpty(),
        )
        scope.launch {
            val outcome = Deliverer(activity, bridge).deliver(request, material)
            busy = false
            if (outcome.ok && outcome.followUp != null) {
                savedNote = outcome
                return@launch
            }
            context.toast(outcome.message)
            if (outcome.ok) onDone()
        }
    }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val target = pendingTarget
        pendingTarget = null
        if (uri != null && target != null) {
            context.keepTree(uri)
            if (target.kind == TargetKind.OBSIDIAN) preferences.obsidianTree = uri else preferences.folderTree = uri
            send(target)
        }
    }

    fun choose(target: DeliveryTarget) {
        if (busy) return
        val needsTree = when (target.kind) {
            TargetKind.FOLDER -> preferences.folderTree == null
            TargetKind.OBSIDIAN -> preferences.obsidianTree == null
            else -> false
        }
        if (needsTree) {
            pendingTarget = target
            context.toast(
                context.getString(
                    if (target.kind == TargetKind.OBSIDIAN) R.string.send_pick_vault else R.string.send_pick_folder,
                ),
            )
            treePicker.launch(null)
            return
        }
        send(target)
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SummaryCard(material)

            OutlinedTextField(
                value = chatName,
                onValueChange = { chatName = it },
                label = { Text(stringResource(R.string.send_chat_name)) },
                placeholder = { Text(stringResource(R.string.send_chat_name_placeholder)) },
                supportingText = { Text(stringResource(R.string.send_chat_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                SectionTitle(stringResource(R.string.send_scene))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = sceneId == null,
                        onClick = { sceneId = null },
                        label = { Text(stringResource(R.string.send_no_scene)) },
                    )
                    library.scenes.forEach { scene ->
                        FilterChip(
                            selected = sceneId == scene.id,
                            onClick = { sceneId = scene.id },
                            label = { Text(scene.name) },
                        )
                    }
                }
                library.scene(sceneId)?.let { ScenePrompt.render(it, lang) }?.let { prompt ->
                    Text(
                        prompt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            if (material.attachments.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.send_attachments, material.attachments.size))
                        Text(
                            stringResource(R.string.send_attachments_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = includeAttachments, onCheckedChange = { includeAttachments = it })
                }
            }

            Column {
                SectionTitle(stringResource(R.string.send_targets))
                val lastTarget = preferences.lastTargetId
                val targets = remember(lastTarget) { TargetCatalog.all.sortedByDescending { it.id == lastTarget } }
                targets.chunked(GRID_COLUMNS).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { target ->
                            TargetTile(
                                target = target,
                                installed = target.kind != TargetKind.AI_APP ||
                                    target.packageName?.let { it in prepared.installedPackages } == true,
                                enabled = !busy,
                                onClick = { choose(target) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            Text(
                stringResource(R.string.send_footnote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (busy) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }

    savedNote?.let { outcome ->
        AlertDialog(
            onDismissRequest = {
                savedNote = null
                onDone()
            },
            title = { Text(stringResource(R.string.send_saved_title)) },
            text = { Text(outcome.message) },
            confirmButton = {
                TextButton(onClick = {
                    savedNote = null
                    outcome.followUp?.let { intent ->
                        val opened = runCatching { context.startActivity(intent) }.isSuccess
                        if (!opened) context.toast(context.getString(R.string.send_obsidian_missing))
                    }
                    onDone()
                }) { Text(stringResource(R.string.send_open_obsidian)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    savedNote = null
                    onDone()
                }) { Text(stringResource(R.string.action_done)) }
            },
        )
    }
}

private const val GRID_COLUMNS = 4

@Composable
private fun SummaryCard(material: Material) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val count = material.messageCount
            Text(
                if (count != null) {
                    stringResource(R.string.send_summary_messages, count, material.attachments.size)
                } else {
                    stringResource(R.string.send_summary_items, material.texts.size, material.attachments.size)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (material.unreadable.isNotEmpty()) {
                Text(
                    stringResource(R.string.send_unreadable, material.unreadable.joinToString("、")),
                    style = MaterialTheme.typography.bodySmall,
                    color = Warning,
                )
            }
            val preview = remember(material) { material.chatText.lineSequence().take(PREVIEW_LINES).joinToString("\n") }
            if (preview.isNotBlank()) {
                Text(
                    preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = PREVIEW_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                )
            }
        }
    }
}

private const val PREVIEW_LINES = 8
