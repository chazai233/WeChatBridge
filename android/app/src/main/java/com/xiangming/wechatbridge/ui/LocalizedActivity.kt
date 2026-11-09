package com.xiangming.wechatbridge.ui

import android.content.Context
import androidx.activity.ComponentActivity
import com.xiangming.wechatbridge.data.Preferences
import com.xiangming.wechatbridge.data.inAppLanguage
import com.xiangming.wechatbridge.core.Lang

/** Every entry point uses the same explicit language, including Android 8–12. */
abstract class LocalizedActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.inAppLanguage(Preferences(newBase).language))
    }

    override fun onResume() {
        super.onResume()
        val expected = if (Preferences(this).language == Lang.ZH) "zh" else "en"
        if (resources.configuration.locales[0].language != expected) recreate()
    }
}