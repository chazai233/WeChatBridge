@file:OptIn(ExperimentalMaterial3Api::class)

package com.xiangming.wechatbridge.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.xiangming.wechatbridge.R
import com.xiangming.wechatbridge.data.SendSubject
import com.xiangming.wechatbridge.ui.theme.WeChatBridgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WeChatBridgeTheme { MainScreen() } }
    }
}

private enum class Tab(val label: Int, val icon: ImageVector) {
    HOME(R.string.tab_home, Icons.Filled.Home),
    HISTORY(R.string.tab_history, AppIcons.History),
    SCENES(R.string.tab_scenes, Icons.Filled.Star),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings),
}

@Composable
private fun MainScreen() {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var subject by rememberSaveable(stateSaver = SubjectSaver) { mutableStateOf<SendSubject?>(null) }
    val pageState = rememberSaveableStateHolder()
    BackHandler(enabled = subject != null || tab != Tab.HOME) {
        if (subject != null) subject = null else tab = Tab.HOME
    }
    Surface(color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = subject,
            transitionSpec = {
                (fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 16 }) togetherWith
                    (fadeOut(tween(120)) + slideOutVertically(tween(180)) { -it / 24 })
            },
            label = "delivery",
        ) { sending ->
            if (sending != null) {
                SendScreen(sending, onClose = { subject = null }, onDone = { subject = null })
            } else {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(if (tab == Tab.HOME) R.string.app_name else tab.label), style = MaterialTheme.typography.titleMedium) },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                        )
                    },
                    bottomBar = {
                        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Row(
                                Modifier.widthIn(max = 480.dp).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    .padding(5.dp).selectableGroup(),
                            ) {
                                Tab.entries.forEach { entry ->
                                    val selected = tab == entry
                                    Column(
                                        Modifier.weight(1f).background(
                                            if (selected) MaterialTheme.colorScheme.background else androidx.compose.ui.graphics.Color.Transparent,
                                            CircleShape,
                                        ).testTag("nav-${entry.name.lowercase()}").selectable(selected, role = Role.Tab, onClick = { tab = entry })
                                            .padding(vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Icon(entry.icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(stringResource(entry.label), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    }
                                }
                            }
                        }
                    },
                ) { padding ->
                    AnimatedContent(
                        targetState = tab,
                        modifier = Modifier.padding(padding).fillMaxSize(),
                        transitionSpec = { fadeIn(tween(160, delayMillis = 60)) togetherWith fadeOut(tween(100)) },
                        label = "tab",
                    ) { page ->
                        pageState.SaveableStateProvider(page) {
                            when (page) {
                                Tab.HOME -> HomeScreen(onSend = { subject = it })
                                Tab.HISTORY -> HistoryScreen(onSend = { subject = it })
                                Tab.SCENES -> ScenesScreen()
                                Tab.SETTINGS -> SettingsScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}

private val SubjectSaver = listSaver<SendSubject?, String>(
    save = { subject ->
        when (subject) {
            null -> listOf("none")
            is SendSubject.Collection -> listOf("collection", subject.id)
            is SendSubject.Batches -> listOf("batches") + subject.ids
        }
    },
    restore = { saved ->
        when (saved.firstOrNull()) {
            "collection" -> SendSubject.Collection(saved[1])
            "batches" -> SendSubject.Batches(saved.drop(1))
            else -> null
        }
    },
)