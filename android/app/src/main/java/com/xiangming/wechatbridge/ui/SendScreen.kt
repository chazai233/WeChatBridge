@file:OptIn(ExperimentalMaterial3Api::class)

package com.xiangming.wechatbridge.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.key
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
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
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val bridge = context.bridge
    val loaded by produceState<Loaded>(Loaded.Loading, subject) {
        value = withContext(Dispatchers.IO) {
            try {
                val prepared = bridge.prepare(subject)
                // Normalization and merging can be large; warm the lazy transcript off the UI thread.
                prepared.material.chatText
                Loaded.Ready(prepared)
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
                    IconButton(onClick = onClose, modifier = Modifier.touchFeedback().background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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

    var chatName by rememberSaveable { mutableStateOf(prepared.suggestedChatName) }
    var sceneId by rememberSaveable { mutableStateOf(initialSceneId(preferences.lastSceneId, library)) }
    var includeAttachments by rememberSaveable { mutableStateOf(preferences.includeAttachments) }
    var targetId by rememberSaveable { mutableStateOf(preferences.lastTargetId ?: TargetCatalog.clipboard.id) }
    val selectedTarget = TargetCatalog.byId(targetId) ?: TargetCatalog.clipboard
    val gridColumns = if (LocalConfiguration.current.screenWidthDp < 400) 3 else 4
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

    Box(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.fillMaxSize().testTag("send-form")) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SummaryCard(material)

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
                        shape = RoundedCornerShape(50),
                    )
                    library.scenes.forEach { scene ->
                        key(scene.id) {
                        FilterChip(
                            selected = sceneId == scene.id,
                            onClick = { sceneId = scene.id },
                            label = { Text(scene.name) },
                            shape = RoundedCornerShape(50),
                        )
                        }
                    }
                }
                library.scene(sceneId)?.let { ScenePrompt.render(it, lang) }?.let { prompt ->
                    Text(
                        prompt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
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
                val targets = TargetCatalog.all
                targets.chunked(gridColumns).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { target ->
                            TargetTile(
                                target = target,
                                installed = target.kind != TargetKind.AI_APP ||
                                    target.packageName?.let { it in prepared.installedPackages } == true,
                                enabled = !busy,
                                onClick = { targetId = target.id },
                                modifier = Modifier.weight(1f),
                                selected = target.id == selectedTarget.id,
                            )
                        }
                        repeat(gridColumns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            OutlinedTextField(
                value = chatName,
                onValueChange = { chatName = it },
                label = { Text(stringResource(R.string.send_chat_name)) },
                placeholder = { Text(stringResource(R.string.send_chat_name_placeholder)) },
                supportingText = { Text(stringResource(R.string.send_chat_name_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                stringResource(R.string.send_footnote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 0.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { choose(selectedTarget) }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("send-confirm").touchFeedback(enabled = !busy),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else {
                        Text(if (selectedTarget.kind == TargetKind.AI_APP) stringResource(R.string.send_confirm_target, selectedTarget.displayName(lang)) else selectedTarget.displayName(lang))
                        Spacer(Modifier.width(8.dp))
                        Icon(AppIcons.ArrowUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
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

@Composable
private fun SummaryCard(material: Material) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val motion = motionAllowed()
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val count = material.messageCount
            Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (count != null) {
                    stringResource(R.string.send_summary_messages, count, material.attachments.size)
                } else {
                    stringResource(R.string.send_summary_items, material.texts.size, material.attachments.size)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
                if (material.texts.isNotEmpty()) {
                    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("preview-toggle")) {
                        Text(stringResource(if (expanded) R.string.send_preview_hide else R.string.send_preview_show))
                    }
                }
            }
            if (material.unreadable.isNotEmpty()) {
                Text(
                    stringResource(R.string.send_unreadable, material.unreadable.joinToString("、")),
                    style = MaterialTheme.typography.bodySmall,
                    color = Warning,
                )
            }
            // Bound preview work for very large chat archives. Full content remains in the delivery.
            val preview = remember(material) { material.chatText.take(6000) }
            if (preview.isNotBlank()) {
                AnimatedVisibility(visible = expanded, enter = fadeIn(tween(if (motion) 140 else 0, easing = EaseOut)), exit = fadeOut(tween(if (motion) 80 else 0, easing = EaseOut))) {
                Text(
                    preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 24,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                )
                }
            }
        }
    }
}
