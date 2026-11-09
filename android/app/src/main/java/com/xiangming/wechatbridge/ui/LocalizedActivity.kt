package com.xiangming.wechatbridge.ui

import android.content.Context
import androidx.activity.ComponentActivity
import com.xiangming.wechatbridge.data.Preferences
import com.xiangming.wechatbridge.data.inAppLanguage

/** Every entry point uses the same explicit language, including Android 8–12. */
abstract class LocalizedActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.inAppLanguage(Preferences(newBase).language))
    }
}