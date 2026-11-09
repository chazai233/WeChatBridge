package com.xiangming.wechatbridge.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.xiangming.wechatbridge.R
import com.xiangming.wechatbridge.bridge
import com.xiangming.wechatbridge.core.KnowledgeDelivery

/** Takes a long-lived grant on a SAF tree, so the folder still works after a reboot. */
fun Context.keepTree(uri: Uri) {
    contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
    )
}

fun Context.treeName(uri: Uri?): String? =
    uri?.let { runCatching { DocumentFile.fromTreeUri(this, it)?.name }.getOrNull() }

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val preferences = context.bridge.preferences
    var folder by remember { mutableStateOf(preferences.folderTree) }
    var vault by remember { mutableStateOf(preferences.obsidianTree) }
    var subfolder by remember { mutableStateOf(preferences.subfolder) }
    var attachments by remember { mutableStateOf(preferences.includeAttachments) }
    val folderName by produceState<String?>(null, folder) { value = withContext(Dispatchers.IO) { context.treeName(folder) } }
    val vaultName by produceState<String?>(null, vault) { value = withContext(Dispatchers.IO) { context.treeName(vault) } }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.keepTree(uri)
            preferences.folderTree = uri
            folder = uri
        }
    }
    val vaultPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.keepTree(uri)
            preferences.obsidianTree = uri
            vault = uri
        }
    }

    Column(
        Modifier.imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SettingsSection(stringResource(R.string.settings_output)) {
            FolderRow(
                title = stringResource(R.string.settings_folder),
                current = folderName,
                onPick = { folderPicker.launch(folder) },
            )
            FolderRow(
                title = stringResource(R.string.settings_vault),
                current = vaultName,
                onPick = { vaultPicker.launch(vault) },
            )
            OutlinedTextField(
                value = subfolder,
                onValueChange = {
                    subfolder = it
                    preferences.subfolder = it
                },
                label = { Text(stringResource(R.string.settings_subfolder)) },
                placeholder = { Text(KnowledgeDelivery.DEFAULT_SUBFOLDER) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.settings_subfolder_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSection(stringResource(R.string.settings_forwarding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_attachments))
                    Text(
                        stringResource(R.string.settings_attachments_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = attachments,
                    onCheckedChange = {
                        attachments = it
                        preferences.includeAttachments = it
                    },
                )
            }
        }

        SettingsSection(stringResource(R.string.settings_language)) {
            Text(stringResource(R.string.settings_language_hint), style = MaterialTheme.typography.bodyMedium)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                OutlinedButton(onClick = {
                    val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                        .setData(Uri.fromParts("package", context.packageName, null))
                    runCatching { context.startActivity(intent) }
                }) { Text(stringResource(R.string.settings_language_open)) }
            }
        }

        SettingsSection(stringResource(R.string.settings_privacy)) {
            Text(stringResource(R.string.settings_privacy_body), style = MaterialTheme.typography.bodyMedium)
        }

        Text(
            stringResource(R.string.settings_version, context.appVersion()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
}

@Composable
private fun FolderRow(title: String, current: String?, onPick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(
                current ?: stringResource(R.string.settings_not_chosen),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onPick) {
            Text(stringResource(if (current == null) R.string.settings_choose else R.string.settings_change))
        }
    }
}

fun Context.appVersion(): String = runCatching {
    @Suppress("DEPRECATION")
    packageManager.getPackageInfo(packageName, 0).versionName
}.getOrNull().orEmpty()