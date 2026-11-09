package com.xiangming.wechatbridge.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager

/** Emil's strong ease-out, shared by custom transitions and touch feedback. */
val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** Native animation clocks also honor duration scale; skip custom transforms entirely at zero. */
@Composable
fun motionAllowed(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun readEnabled() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    var enabled by remember(resolver) { mutableStateOf(readEnabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled = readEnabled() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled && LocalInputModeManager.current.inputMode == InputMode.Touch
}

/** Observes touch without consuming it, preserving Material click, ripple and scroll cancellation. */
@Composable
fun Modifier.touchFeedback(enabled: Boolean = true): Modifier {
    val allowed = motionAllowed() && enabled
    var pressed by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(
        targetValue = if (pressed && allowed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 120, easing = EaseOut),
        label = "touch-press",
    )
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .pointerInput(allowed) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = allowed
                try { waitForUpOrCancellation() } finally { pressed = false }
            }
        }
}