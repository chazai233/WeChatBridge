package com.xiangming.wechatbridge.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.xiangming.wechatbridge.R
import com.xiangming.wechatbridge.data.SendSubject
import com.xiangming.wechatbridge.ui.theme.WeChatBridgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WeChatBridgeTheme {
                MainScreen()
            }
        }
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
    var subject by remember { mutableStateOf<SendSubject?>(null) }

    val sending = subject
    if (sending != null) {
        BackHandler { subject = null }
        SendScreen(subject = sending, onClose = { subject = null }, onDone = { subject = null })
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(stringResource(entry.label)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.HOME -> HomeScreen(onSend = { subject = it })
                Tab.HISTORY -> HistoryScreen(onSend = { subject = it })
                Tab.SCENES -> ScenesScreen()
                Tab.SETTINGS -> SettingsScreen()
            }
        }
    }
}
