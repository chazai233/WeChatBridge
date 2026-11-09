package com.xiangming.wechatbridge.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiangming.wechatbridge.R
import com.xiangming.wechatbridge.bridge
import com.xiangming.wechatbridge.core.BatchOutcome
import com.xiangming.wechatbridge.core.CollectionStatus
import com.xiangming.wechatbridge.core.DeliveryTarget
import com.xiangming.wechatbridge.core.Lang
import com.xiangming.wechatbridge.core.OutcomeKind
import com.xiangming.wechatbridge.core.TargetKind
import com.xiangming.wechatbridge.ui.theme.Brand
import com.xiangming.wechatbridge.ui.theme.Danger
import com.xiangming.wechatbridge.ui.theme.Warning
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Material icons that are not in `material-icons-core`, built from their 24dp path data. */
object AppIcons {
    val More = icon("More", "M12,4a2,2 0,1 0,0 4,2 2,0 0,0 0,-4zM12,10a2,2 0,1 0,0 4,2 2,0 0,0 0,-4zM12,16a2,2 0,1 0,0 4,2 2,0 0,0 0,-4z")
    val ArrowUp = icon("ArrowUp", "M11,20V7.83l-5.59,5.58L4,12l8,-8 8,8 -1.41,1.41L13,7.83V20z")
    val Chevron = icon("Chevron", "M9.29,6.71 13.58,11 9.29,15.29 10.71,16.71 16.42,11 10.71,5.29z")
    val Help = icon("Help", "M12,2a10,10 0,1 0,0 20,10 10,0 0,0 0,-20zM13,18h-2v-2h2zM15.07,10.25l-0.9,0.92C13.45,11.9 13,12.5 13,14h-2v-0.5c0,-1.1 0.45,-2.1 1.17,-2.83l1.24,-1.26A2,2 0,1 0,10 8H8a4,4 0,1 1,7.07 2.25z")
    val Folder = icon("Folder", "M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z")
    val ContentCopy = icon("ContentCopy", "M16,1H4c-1.1,0 -2,0.9 -2,2v14h2V3h12V1zM19,5H8c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zM19,21H8V7h11v14z")
    val ContentPaste = icon("ContentPaste", "M19,2h-4.18C14.4,0.84 13.3,0 12,0c-1.3,0 -2.4,0.84 -2.82,2H5c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM12,2c0.55,0 1,0.45 1,1s-0.45,1 -1,1 -1,-0.45 -1,-1 0.45,-1 1,-1zM19,20H5V4h2v3h10V4h2v16z")
    val History = icon("History", "M13,3c-4.97,0 -9,4.03 -9,9H1l3.89,3.89 0.07,0.14L9,12H6c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08V8H12z")

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black)).build()
}

fun Context.findActivity(): Activity {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    throw IllegalStateException("No activity in context")
}

fun Context.toast(message: String) {
    val show = { Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show() }
    if (Looper.myLooper() == Looper.getMainLooper()) show()
    else Handler(Looper.getMainLooper()).post { show() }
}

fun Context.copyText(text: String): Boolean = try {
    (getSystemService(ClipboardManager::class.java) ?: throw IllegalStateException())
        .setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text))
    true
} catch (ignored: RuntimeException) {
    false
}

/** All text items on the clipboard. Android 10+ only allows this while the app is in front. */
fun Context.clipboardText(): String {
    val clip = getSystemService(ClipboardManager::class.java)?.primaryClip ?: return ""
    return (0 until clip.itemCount)
        .mapNotNull { clip.getItemAt(it).coerceToText(this)?.toString() }
        .filter { it.isNotBlank() }
        .joinToString("\n")
}

@Composable
fun currentLang(): Lang = LocalContext.current.bridge.lang()

private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun formatTime(instant: Instant): String = timeFormat.format(instant.atZone(ZoneId.systemDefault()))

/** A logo from `shared/AppLogos`, decoded small: the sources are 1024 px. */
@Composable
fun AssetLogo(asset: String, size: Dp) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, asset) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(asset).use { stream ->
                    BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = 4 })
                }
            }.getOrNull()?.asImageBitmap()
        }
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.22f)).testTag("logo-$asset"),
        )
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.22f)).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

@Composable
fun TargetIcon(target: DeliveryTarget, size: Dp = 48.dp) {
    val logo = target.logoAsset
    if (logo != null) {
        AssetLogo(logo, size)
        return
    }
    val vector = when (target.kind) {
        TargetKind.CLIPBOARD -> AppIcons.ContentCopy
        TargetKind.FOLDER -> AppIcons.Folder
        TargetKind.SYSTEM_SHARE -> Icons.Filled.Share
        else -> null
    }
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(size * 0.22f),
        color = if (vector == null) Color(0xFF4D6BFE) else MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (vector != null) {
                Icon(vector, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            } else {
                // DeepSeek has no logo in shared/AppLogos yet.
                Text(
                    target.enName.take(1),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}

@Composable
fun TargetTile(
    target: DeliveryTarget,
    installed: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        TargetIcon(target)
        Spacer(Modifier.height(6.dp))
        Text(
            target.displayName(currentLang()),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!installed) {
            Text(
                stringResource(R.string.target_not_installed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun StatusChip(label: String, color: Color) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun OutcomeChip(outcome: BatchOutcome?, collecting: Boolean = false) {
    when {
        outcome == null && collecting -> StatusChip(stringResource(R.string.status_collecting), Brand)
        outcome == null -> StatusChip(stringResource(R.string.status_pending), Warning)
        outcome.kind == OutcomeKind.DELIVERED -> StatusChip(stringResource(R.string.status_delivered), Brand)
        outcome.kind == OutcomeKind.COPIED -> StatusChip(stringResource(R.string.status_copied), Brand)
        outcome.kind == OutcomeKind.FAILED -> StatusChip(stringResource(R.string.status_failed), Danger)
        else -> StatusChip(stringResource(R.string.status_expired), Warning)
    }
}

@Composable
fun CollectionStatusChip(status: CollectionStatus) {
    when (status) {
        CollectionStatus.COLLECTING -> StatusChip(stringResource(R.string.status_collecting), Brand)
        CollectionStatus.DRAFT -> StatusChip(stringResource(R.string.status_draft), Warning)
        CollectionStatus.DELIVERING -> StatusChip(stringResource(R.string.status_delivering), Warning)
        CollectionStatus.DELIVERED -> StatusChip(stringResource(R.string.status_delivered), Brand)
        CollectionStatus.RETRY -> StatusChip(stringResource(R.string.status_retry), Danger)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 12.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A single native sheet treatment for editable content and help. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.86f).dp)
                .imePadding().verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.touchFeedback().background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close), modifier = Modifier.size(20.dp))
                }
            }
            content()
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(20.dp).size(28.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun RecordActions(primary: String?, onPrimary: () -> Unit, secondary: List<Pair<String, () -> Unit>>) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        if (primary != null) TextButton(onClick = onPrimary) { Text(primary) }
        else Spacer(Modifier.weight(1f))
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(AppIcons.More, contentDescription = stringResource(R.string.action_more), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                secondary.forEach { (label, action) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { expanded = false; action() })
                }
            }
        }
    }
}
