package com.xiangming.wechatbridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiangming.wechatbridge.R
import com.xiangming.wechatbridge.bridge
import com.xiangming.wechatbridge.core.CollectionStatus
import com.xiangming.wechatbridge.data.BatchView
import com.xiangming.wechatbridge.data.CollectionView
import com.xiangming.wechatbridge.data.HistoryState
import com.xiangming.wechatbridge.data.SendSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface PendingDelete {
    data class Batch(val view: BatchView) : PendingDelete
    data class Collection(val view: CollectionView) : PendingDelete
}

/** 记录: every batch and collection with its status, and resend / copy / delete. */
@Composable
fun HistoryScreen(onSend: (SendSubject) -> Unit) {
    val context = LocalContext.current
    val bridge = context.bridge
    val scope = rememberCoroutineScope()
    val revision by bridge.revision.collectAsState()
    val history by produceState<HistoryState?>(null, revision) {
        value = withContext(Dispatchers.IO) { runCatching { bridge.historyState() }.getOrNull() }
    }
    var deleting by remember { mutableStateOf<PendingDelete?>(null) }

    val state = history
    if (state != null && state.batches.isEmpty() && state.collections.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.history_empty),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(stringResource(R.string.tab_history), style = MaterialTheme.typography.headlineSmall)
        }
        val collections = state?.collections.orEmpty()
        if (collections.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.history_collections)) }
            items(collections, key = { "c-" + it.collection.id }) { view ->
                CollectionRow(
                    view = view,
                    onSend = { onSend(SendSubject.Collection(view.collection.id)) },
                    onResume = {
                        scope.launch(Dispatchers.IO) {
                            runCatching { bridge.updateLedger { it.resume(view.collection.id) } }
                        }
                    },
                    onDelete = { deleting = PendingDelete.Collection(view) },
                )
            }
        }
        val batches = state?.batches.orEmpty()
        if (batches.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.history_batches)) }
            items(batches, key = { "b-" + it.id }) { view ->
                BatchRow(
                    view = view,
                    onResend = { onSend(SendSubject.Batches(listOf(view.id))) },
                    onCopy = {
                        scope.launch {
                            val text = withContext(Dispatchers.IO) { runCatching { bridge.chatText(listOf(view.id)) }.getOrNull() }
                            val ok = !text.isNullOrEmpty() && context.copyText(text)
                            context.toast(context.getString(if (ok) R.string.detail_copied else R.string.detail_copy_failed))
                        }
                    },
                    onDelete = { deleting = PendingDelete.Batch(view) },
                )
            }
        }
    }

    deleting?.let { pending ->
        val message = when (pending) {
            is PendingDelete.Batch -> stringResource(R.string.batch_delete_message)
            is PendingDelete.Collection ->
                stringResource(R.string.collection_discard_message, pending.view.collection.batchIds.size)
        }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch(Dispatchers.IO) {
                        when (pending) {
                            is PendingDelete.Batch -> bridge.deleteBatch(pending.view.id)
                            is PendingDelete.Collection -> bridge.deleteCollection(pending.view.collection.id)
                        }
                    }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun CollectionRow(view: CollectionView, onSend: () -> Unit, onResume: () -> Unit, onDelete: () -> Unit) {
    val collection = view.collection
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    collection.name.ifBlank { stringResource(R.string.collection_default_name, formatTime(collection.createdAt)) },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                CollectionStatusChip(collection.status)
            }
            Text(
                stringResource(R.string.collection_summary, collection.batchIds.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            listOfNotNull(collection.targetName, collection.sceneName, collection.detail)
                .takeIf { it.isNotEmpty() }
                ?.let { Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (collection.status != CollectionStatus.DELIVERING) {
                    TextButton(onClick = onSend) {
                        Text(
                            stringResource(
                                if (collection.status == CollectionStatus.DELIVERED) R.string.action_resend else R.string.action_send,
                            ),
                        )
                    }
                }
                if (collection.status == CollectionStatus.DRAFT || collection.status == CollectionStatus.RETRY) {
                    TextButton(onClick = onResume) { Text(stringResource(R.string.collection_resume)) }
                }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }
}

@Composable
private fun BatchRow(view: BatchView, onResend: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit) {
    val state = view.state
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    view.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                OutcomeChip(view.outcome, collecting = view.collectionId != null)
            }
            Text(
                stringResource(R.string.batch_meta, formatTime(view.createdAt), view.itemCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            listOfNotNull(state?.targetName, state?.sceneName, view.outcome?.detail)
                .takeIf { it.isNotEmpty() }
                ?.let { Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onResend) {
                    Text(stringResource(if (view.outcome == null) R.string.action_send else R.string.action_resend))
                }
                TextButton(onClick = onCopy) { Text(stringResource(R.string.action_copy)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }
}
