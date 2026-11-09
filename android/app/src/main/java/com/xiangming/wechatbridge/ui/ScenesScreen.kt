package com.xiangming.wechatbridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiangming.wechatbridge.R
import com.xiangming.wechatbridge.bridge
import com.xiangming.wechatbridge.core.Scene
import com.xiangming.wechatbridge.core.SceneLibrary
import com.xiangming.wechatbridge.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 场景: the prompts offered at send time. Add, edit, delete, pick the default. */
@Composable
fun ScenesScreen() {
    val context = LocalContext.current
    val bridge = context.bridge
    val scope = rememberCoroutineScope()
    val lang = currentLang()
    val revision by bridge.revision.collectAsState()
    val library by produceState<SceneLibrary?>(null, revision) {
        value = withContext(Dispatchers.IO) { bridge.sceneLibrary() }
    }
    var editing by remember { mutableStateOf<Scene?>(null) }
    var deleting by remember { mutableStateOf<Scene?>(null) }

    fun update(transform: (SceneLibrary) -> SceneLibrary) {
        scope.launch(Dispatchers.IO) { bridge.updateScenes(transform) }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.tab_scenes),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { editing = Scene(name = "", instruction = "", outputSpec = "") }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.scene_new))
                    }
                }
                Text(
                    stringResource(R.string.scenes_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        val current = library
        if (current != null) {
            items(current.scenes, key = { it.id }) { scene ->
                SceneCard(
                    scene = scene,
                    isDefault = scene.id == current.defaultSceneId,
                    onEdit = { editing = scene },
                    onDelete = { deleting = scene },
                    onMakeDefault = { update { it.withDefault(scene.id) } },
                )
            }
            item {
                TextButton(onClick = { update { it.restoringStarters(lang) } }) {
                    Text(stringResource(R.string.scene_restore_defaults))
                }
            }
        }
    }

    editing?.let { scene ->
        SceneEditor(
            initial = scene,
            onDismiss = { editing = null },
            onSave = { saved ->
                editing = null
                update { it.upsert(saved) }
            },
        )
    }

    deleting?.let { scene ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.scene_delete_message, scene.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    update { it.remove(scene.id) }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun SceneCard(
    scene: Scene,
    isDefault: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMakeDefault: () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(scene.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (isDefault) StatusChip(stringResource(R.string.scene_default), Brand)
            }
            Text(
                scene.instruction,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
                if (!isDefault) {
                    TextButton(onClick = onMakeDefault) { Text(stringResource(R.string.scene_make_default)) }
                }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }
}

@Composable
private fun SceneEditor(initial: Scene, onDismiss: () -> Unit, onSave: (Scene) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var instruction by remember { mutableStateOf(initial.instruction) }
    var outputSpec by remember { mutableStateOf(initial.outputSpec) }
    val valid = name.isNotBlank() && (instruction.isNotBlank() || outputSpec.isNotBlank())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initial.name.isEmpty()) R.string.scene_new else R.string.scene_edit_title))
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.scene_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    label = { Text(stringResource(R.string.scene_field_instruction)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                )
                OutlinedTextField(
                    value = outputSpec,
                    onValueChange = { outputSpec = it },
                    label = { Text(stringResource(R.string.scene_field_output)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(initial.copy(name = name.trim(), instruction = instruction.trim(), outputSpec = outputSpec.trim()))
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
