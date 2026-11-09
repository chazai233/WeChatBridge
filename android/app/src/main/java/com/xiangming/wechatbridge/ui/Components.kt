package com.xiangming.wechatbridge.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiangming.wechatbridge.R
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
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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
fun currentLang(): Lang = Lang.of(LocalConfiguration.current.locales[0].language)

private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun formatTime(instant: Instant): String = timeFormat.format(instant.atZone(ZoneId.systemDefault()))

/** A logo from `shared/AppLogos`, decoded small: the sources are 1024 px. */
@Composable
fun AssetLogo(asset: String, size: Dp) {
    val context = LocalContext.current
    val bitmap: ImageBitmap? = remember(asset) {
        runCatching {
            context.assets.open(asset).use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = 4 })
            }
        }.getOrNull()?.asImageBitmap()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.22f)),
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
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
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
    Surface(shape = CircleShape, color = color.copy(alpha = 0.14f)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
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
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
