package com.xiangming.wechatbridge

import android.app.Application
import android.content.Context
import com.xiangming.wechatbridge.data.Bridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WeChatBridgeApplication : Application() {
    lateinit var bridge: Bridge
        private set

    /** Work that must outlive the screen that started it, such as recording an outcome. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        bridge = Bridge(this)
        scope.launch { bridge.housekeeping() }
    }
}

val Context.bridge: Bridge get() = (applicationContext as WeChatBridgeApplication).bridge
