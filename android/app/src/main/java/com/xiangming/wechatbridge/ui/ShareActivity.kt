package com.xiangming.wechatbridge.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiangming.wechatbridge.R
import com.xiangming.wechatbridge.bridge
import com.xiangming.wechatbridge.core.BatchInsight
import com.xiangming.wechatbridge.data.Imported
import com.xiangming.wechatbridge.data.IncomingIntents
import com.xiangming.wechatbridge.data.SendSubject
import com.xiangming.wechatbridge.data.userError
import com.xiangming.wechatbridge.ui.theme.WeChatBridgeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Behind the two share entries. 「微信流」 imports and goes straight to the
 * send screen; 「分批收集到微信流」 imports into the open collection and offers
 * to go back to WeChat for the next batch.
 */
class ShareActivity : LocalizedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Recreated after process death: the share was already imported (or the
        // URI grant is gone), so there is nothing left to do here.
        if (savedInstanceState != null) {
            finish()
            return
        }
        val collect = intent.component?.className?.endsWith(".ShareCollect") == true
        val incoming = intent
        setContent {
            WeChatBridgeTheme {
                ShareFlow(incoming, collect, onFinish = { finish() })
            }
        }
    }
}

private sealed interface ShareState {
    data object Importing : ShareState
    data class Imported(val result: com.xiangming.wechatbridge.data.Imported) : ShareState
    data class Failed(val message: String) : ShareState
}

@Composable
private fun ShareFlow(intent: Intent, collect: Boolean, onFinish: () -> Unit) {
    val context = LocalContext.current
    val bridge = context.bridge
    var state by remember { mutableStateOf<ShareState>(ShareState.Importing) }
    var sendCollection by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        state = withContext(Dispatchers.IO) {
            try {
                val items = IncomingIntents.read(context, intent)
                if (items.isEmpty()) {
                    ShareState.Failed(context.getString(R.string.share_nothing))
                } else {
                    ShareState.Imported(bridge.importShare(items, collect))
                }
            } catch (error: Exception) {
                ShareState.Failed(context.getString(R.string.import_failed, context.userError(error)))
            }
        }
    }

    when (val current = state) {
        ShareState.Importing -> Centered {
            CircularProgressIndicator()
            Text(stringResource(R.string.share_importing), modifier = Modifier.padding(top = 16.dp))
        }
        is ShareState.Failed -> Centered {
            Text(current.message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
            Button(onClick = onFinish, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.action_close))
            }
        }
        is ShareState.Imported -> {
            val result = current.result
            val collectionId = result.collectionId
            when {
                collectionId == null -> SendScreen(
                    subject = SendSubject.Batches(listOf(result.batchId)),
                    onClose = onFinish,
                    onDone = onFinish,
                )
                sendCollection -> SendScreen(
                    subject = SendSubject.Collection(collectionId),
                    onClose = { sendCollection = false },
                    onDone = onFinish,
                )
                else -> Collected(
                    result = result,
                    onContinue = onFinish,
                    onSendNow = { sendCollection = true },
                )
            }
        }
    }
}

@Composable
private fun Collected(result: Imported, onContinue: () -> Unit, onSendNow: () -> Unit) {
    val context = LocalContext.current
    val bridge = context.bridge
    val insight by produceState<BatchInsight?>(null, result.collectionId) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                bridge.ledger().collection(result.collectionId.orEmpty())?.let { bridge.insight(it) }
            }.getOrNull()
        }
    }
    Centered {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
        Text(
            stringResource(R.string.collect_added, result.collectedCount),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        val count = insight?.messageCount
        if (count != null) {
            Text(stringResource(R.string.collection_summary_messages, result.collectedCount, count))
        }
        insight?.last?.let { last ->
            Text(
                stringResource(R.string.collection_last_message, last.sender, last.text.take(40)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            stringResource(R.string.collect_next_hint),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.collect_continue))
        }
        OutlinedButton(onClick = onSendNow, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.collection_finish_send))
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
            }
        }
    }
}
