package com.xiangming.wechatbridge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** WeChat green, the brand colour every port uses (#07C160). */
val Brand = Color(0xFF07C160)
val BrandPressed = Color(0xFF0FA845)
val Warning = Color(0xFFB26A00)
val Danger = Color(0xFFD93025)

private val LightColors = lightColorScheme(
    primary = Color(0xFF171717),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF0F0F0),
    onPrimaryContainer = Color(0xFF171717),
    secondary = Color(0xFF626262),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECECEC),
    onSecondaryContainer = Color(0xFF171717),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF171717),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF737373),
    outline = Color(0xFFD4D4D4),
    outlineVariant = Color(0xFFECECEC),
    surfaceContainer = Color(0xFFF5F5F5),
    surfaceContainerLow = Color(0xFFFAFAFA),
    error = Danger,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF4F4F4),
    onPrimary = Color(0xFF171717),
    primaryContainer = Color(0xFF292929),
    onPrimaryContainer = Color(0xFFF4F4F4),
    secondary = Color(0xFFB0B0B0),
    onSecondary = Color(0xFF171717),
    secondaryContainer = Color(0xFF303030),
    onSecondaryContainer = Color(0xFFF4F4F4),
    background = Color(0xFF101010),
    onBackground = Color(0xFFF4F4F4),
    surface = Color(0xFF101010),
    onSurface = Color(0xFFF4F4F4),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFA3A3A3),
    outline = Color(0xFF484848),
    outlineVariant = Color(0xFF2B2B2B),
    surfaceContainer = Color(0xFF1E1E1E),
    surfaceContainerLow = Color(0xFF171717),
    error = Color(0xFFFF8A80),
)

@Composable
fun WeChatBridgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(22.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(32.dp),
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),
            headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp),
            titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
            bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 19.sp),
        ),
        content = content,
    )
}
