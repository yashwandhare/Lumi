package com.trace.ui.theme

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * True when the user has turned animations off system-wide.
 *
 * Every animation in Trace must read this, including the mascot's breathing. It is a
 * theme-level value rather than a per-screen check so no screen can forget it.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * The single theme wrapper. Everything renders inside it.
 *
 * Material 3 components pick up Trace's colours, type, and shapes from here, so a stock
 * `Button` or `TextField` is already correct. The spacing scale and the reduced-motion
 * flag are provided alongside, since Material 3 has no slot for either.
 */
@Composable
fun TraceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalTraceSpacing provides TraceSpacing(),
        LocalReducedMotion provides rememberReducedMotion(),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) TraceDarkColorScheme else TraceLightColorScheme,
            typography = TraceTypography,
            shapes = TraceShapes,
            content = content,
        )
    }
}

/**
 * The spacing scale, read as `MaterialTheme.spacing.lg`.
 *
 * Prefer this over literal dp values. A screen that invents its own padding is the first
 * step towards a screen that looks like it came from a different app.
 */
val MaterialTheme.spacing: TraceSpacing
    @Composable @ReadOnlyComposable get() = LocalTraceSpacing.current

/**
 * Observed rather than read once. Android does not deliver a configuration change when
 * the animation setting flips, so a plain read would leave a running app animating after
 * the user asked it to stop.
 */
@Composable
private fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var reduced by remember(resolver) { mutableStateOf(animationsDisabled(resolver)) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = animationsDisabled(resolver)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

private fun animationsDisabled(resolver: ContentResolver): Boolean =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
