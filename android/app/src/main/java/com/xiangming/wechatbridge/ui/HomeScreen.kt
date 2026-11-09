package com.xiangming.wechatbridge.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiangming.wechatbridge.R
import com.xiangming.wechatbridge.bridge
import com.xiangming.wechatbridge.core.IncomingItem
import com.xiangming.wechatbridge.data.BatchView
import com.xiangming.wechatbridge.data.CollectionView
import com.xiangming.wechatbridge.data.HomeState
import com.xiangming.wechatbridge.data.SendSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(onSend: (SendSubject) -> Unit) {
    val context = LocalContext.current
    val bridge = context.bridge
    val scope = rememberCoroutineScope()
    val revision by bridge.revision.collectAsState()
    val home by produceState<HomeState?>(null, revision) {
        value = withContext(Dispatchers.IO) { runCatching { bridge.homeState() }.getOrNull() }
    }
    var paste by rememberSaveable { mutableStateOf<String?>(null) }
    var help by rememberSaveable { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var discarding by remember { mutableStateOf<CollectionView?>(null) }

    Column(Modifier.fillMaxSize().testTag("home")) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "welcome") {
                Header()
                OutlinedButton(
                    onClick = { help = true },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) { Text(stringResource(R.string.home_share_help)) }
            }
            home?.collecting?.let { view ->
                item(key = "collecting") {
                    CollectingCard(
                        view = view,
                        onSend = { onSend(SendSubject.Collection(view.collection.id)) },
                        onPark = { scope.launch(Dispatchers.IO) { bridge.updateLedger { it.parkCurrent() } } },
                        onDiscard = { discarding = view },
                    )
                }
            }
            val pending = home?.pending.orEmpty()
            if (pending.isNotEmpty()) {
                item(key = "pending-title") { SectionTitle(stringResource(R.string.home_pending)) }
                items(pending, key = { it.id }) { batch ->
                    PendingRow(batch, onSend = { onSend(SendSubject.Batches(listOf(batch.id))) })
                }
            }
        }
        PasteCard(onPaste = { paste = context.clipboardText() })
    }
    if (help) {
        EditorSheet(stringResource(R.string.howto_title), onDismiss = { help = false }) { HowToContent() }
    }

    paste?.let { initial ->
        PasteSheet(
            initial = initial,
            busy = importing,
            onDismiss = { if (!importing) paste = null },
            onConfirm = { text, collect ->
                importing = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            bridge.importShare(
                                listOf(IncomingItem.text(context.getString(R.string.pasted_text_file_name), text)),
                                collect,
                            )
                        }
                    }
                    importing = false
                    result.onSuccess { imported ->
                        paste = null
                        if (collect) {
                            context.toast(context.getString(R.string.collect_added, imported.collectedCount))
                        } else {
                            onSend(SendSubject.Batches(listOf(imported.batchId)))
                        }
                    }.onFailure {
                        context.toast(context.getString(R.string.import_failed, it.localizedMessage ?: it.toString()))
                    }
                }
            },
        )
    }

    discarding?.let { view ->
        AlertDialog(
            onDismissRequest = { discarding = null },
            title = { Text(stringResource(R.string.collection_discard_title)) },
            text = { Text(stringResource(R.string.collection_discard_message, view.collection.batchIds.size)) },
            confirmButton = {
                TextButton(onClick = {
                    discarding = null
                    scope.launch(Dispatchers.IO) { bridge.deleteCollection(view.collection.id) }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { discarding = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun Header() {
    Column(
        Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(72.dp),
        )
            Text(
                stringResource(R.string.home_headline),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
    }
}

@Composable
private fun PasteCard(onPaste: () -> Unit) {
    Surface(
        onClick = onPaste,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).testTag("paste-entry").touchFeedback(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.ContentPaste, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.home_paste_action), modifier = Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyMedium)
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Icon(AppIcons.ArrowUp, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(12.dp).size(22.dp))
            }
        }
    }
}

@Composable
private fun CollectingCard(view: CollectionView, onSend: () -> Unit, onPark: () -> Unit, onDiscard: () -> Unit) {
    val collection = view.collection
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.home_collecting_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                CollectionStatusChip(collection.status)
            }
            val insight = view.insight
            val count = insight?.messageCount
            Text(
                if (count != null) {
                    stringResource(R.string.collection_summary_messages, collection.batchIds.size, count)
                } else {
                    stringResource(R.string.collection_summary, collection.batchIds.size)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            insight?.last?.let { last ->
                Text(
                    stringResource(R.string.collection_last_message, last.sender, last.text.take(40)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Button(onClick = onSend, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).touchFeedback()) { Text(stringResource(R.string.collection_finish_send)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onPark) { Text(stringResource(R.string.collection_park)) }
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }
}

@Composable
private fun PendingRow(batch: BatchView, onSend: () -> Unit) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onSend).padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Icon(AppIcons.History, contentDescription = null, modifier = Modifier.padding(12.dp).size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(batch.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    formatTime(batch.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(AppIcons.Chevron, contentDescription = stringResource(R.string.action_send), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
}

@Composable
private fun HowToContent() {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(R.string.howto_share), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.howto_open_with), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.howto_paste), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.howto_collect), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.howto_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
}

@Composable
private fun PasteSheet(initial: String, busy: Boolean, onDismiss: () -> Unit, onConfirm: (String, Boolean) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    EditorSheet(stringResource(R.string.home_paste_title), onDismiss) {
            Text(stringResource(R.string.home_paste_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial.isBlank() && text.isBlank()) {
                    Text(
                        stringResource(R.string.paste_dialog_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    enabled = !busy,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 300.dp).testTag("paste-input"),
                    placeholder = { Text(stringResource(R.string.paste_dialog_placeholder)) },
                )
            }
            Button(enabled = text.isNotBlank() && !busy, onClick = { onConfirm(text, false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("paste-confirm").touchFeedback(enabled = text.isNotBlank() && !busy)) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.paste_dialog_send))
            }
                OutlinedButton(enabled = text.isNotBlank() && !busy, onClick = { onConfirm(text, true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.paste_dialog_collect))
                }
    }
}
