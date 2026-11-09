package com.xiangming.wechatbridge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** WeChat green, the brand colour every port uses (#07C160). */
val Brand = Color(0xFF07C160)
val BrandPressed = Color(0xFF0FA845)
val Warning = Color(0xFFB26A00)
val Danger = Color(0xFFD93025)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF6E7),
    onPrimaryContainer = Color(0xFF00391A),
    secondary = Color(0xFF4A5055),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6EBE8),
    onSecondaryContainer = Color(0xFF1B1F1C),
    background = Color(0xFFF7FAF8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF2F5F3),
    onSurfaceVariant = Color(0xFF5B635E),
    outline = Color(0xFFD8DEDA),
    error = Danger,
)

private val DarkColors = darkColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0B4A2A),
    onPrimaryContainer = Color(0xFFDDF6E7),
    secondary = Color(0xFFD7DCD9),
    onSecondary = Color(0xFF1B1F1C),
    secondaryContainer = Color(0xFF2E3430),
    onSecondaryContainer = Color(0xFFE6EBE8),
    background = Color(0xFF121513),
    surface = Color(0xFF1B1F1C),
    surfaceVariant = Color(0xFF242825),
    onSurfaceVariant = Color(0xFFB8C0BB),
    outline = Color(0xFF464D48),
    error = Color(0xFFFF8A80),
)

@Composable
fun WeChatBridgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
