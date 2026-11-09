package com.xiangming.wechatbridge.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
    var paste by remember { mutableStateOf<String?>(null) }
    var discarding by remember { mutableStateOf<CollectionView?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header() }
        item { PasteCard(onPaste = { paste = context.clipboardText() }) }
        home?.collecting?.let { view ->
            item {
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
            item { SectionTitle(stringResource(R.string.home_pending)) }
            items(pending, key = { it.id }) { batch ->
                PendingRow(batch, onSend = { onSend(SendSubject.Batches(listOf(batch.id))) })
            }
        }
        item { HowToCard() }
    }

    paste?.let { initial ->
        PasteDialog(
            initial = initial,
            onDismiss = { paste = null },
            onConfirm = { text, collect ->
                paste = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            bridge.importShare(
                                listOf(IncomingItem.text(context.getString(R.string.pasted_text_file_name), text)),
                                collect,
                            )
                        }
                    }
                    result.onSuccess { imported ->
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PasteCard(onPaste: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_paste_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.home_paste_body), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onPaste) {
                Icon(AppIcons.ContentPaste, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_paste_action))
            }
        }
    }
}

@Composable
private fun CollectingCard(view: CollectionView, onSend: () -> Unit, onPark: () -> Unit, onDiscard: () -> Unit) {
    val collection = view.collection
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSend) { Text(stringResource(R.string.collection_finish_send)) }
                OutlinedButton(onClick = onPark) { Text(stringResource(R.string.collection_park)) }
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }
}

@Composable
private fun PendingRow(batch: BatchView, onSend: () -> Unit) {
    Card {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(batch.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    formatTime(batch.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onSend) { Text(stringResource(R.string.action_send)) }
        }
    }
}

@Composable
private fun HowToCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.howto_title), style = MaterialTheme.typography.titleMedium)
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
}

@Composable
private fun PasteDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String, Boolean) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paste_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial.isBlank()) {
                    Text(
                        stringResource(R.string.paste_dialog_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
                    placeholder = { Text(stringResource(R.string.paste_dialog_placeholder)) },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text, false) }) {
                Text(stringResource(R.string.paste_dialog_send))
            }
        },
        dismissButton = {
            Row {
                TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text, true) }) {
                    Text(stringResource(R.string.paste_dialog_collect))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
