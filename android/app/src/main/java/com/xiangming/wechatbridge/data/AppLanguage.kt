package com.xiangming.wechatbridge.data

import android.content.Context
import android.content.res.Configuration
import com.xiangming.wechatbridge.R
import com.xiangming.wechatbridge.core.Lang
import java.io.IOException
import java.util.Locale

/** Override locale only: night mode, density, font scale and insets still follow Android. */
fun Context.inAppLanguage(language: Lang): Context {
    val locale = if (language == Lang.ZH) Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH
    val languageOverride = Configuration().apply {
        fontScale = 0f
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return createConfigurationContext(languageOverride)
}

/** Platform and archive exception details may be English; keep actionable app errors localized. */
fun Context.userError(error: Throwable): String = getString(
    when (error) {
        is SecurityException -> R.string.error_permission
        is IOException -> R.string.error_io
        else -> R.string.error_retry
    },
)