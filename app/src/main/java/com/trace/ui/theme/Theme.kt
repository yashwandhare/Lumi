package com.trace.ui.theme

import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/**
 * True when the user has turned animations off system-wide.
 *
 * This answers "the user asked for stillness", which governs more than animation — it is also the
 * reason to skip a decorative transition entirely rather than shorten it. For the narrower
 * question "may I run this loop right now", read [LocalMotionEnabled], which folds this together
 * with battery saver and lifecycle state.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * §4: every Material shape token is 16dp, so a stock `Button` or `Card` is already correct
 * without being told. The radii §4 assigns to specific roles — the 28dp input, the 24dp tile, the
 * square full-bleed panel — live in [TraceShape] because Material has no slot for a role.
 */
val CohesiveShapes = Shapes(
    extraSmall = TraceShape.default,
    small = TraceShape.default,
    medium = TraceShape.default,
    large = TraceShape.default,
    extraLarge = TraceShape.default,
)

/**
 * The single theme wrapper. Everything renders inside it.
 *
 * Material 3 components pick up Trace's colours, type, and shapes from here, so a stock `Button`
 * or `TextField` is already correct. The spacing scale and the two motion flags are provided
 * alongside, since Material 3 has no slot for either.
 *
 * @param darkTheme defaults to the system setting, per `DESIGN_LANGUAGE.md` §9's "first launch
 *   follows system". Callers that have loaded a persisted user override pass it explicitly.
 */
@Composable
fun TraceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val batterySaver = rememberBatterySaver()
    val onScreen = LocalLifecycleOwner.current.lifecycle
        .currentStateAsState().value
        .isAtLeast(Lifecycle.State.STARTED)

    CompositionLocalProvider(
        LocalTraceSpacing provides TraceSpacing(),
        LocalReducedMotion provides reducedMotion,
        LocalMotionEnabled provides (!reducedMotion && !batterySaver && onScreen),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) TraceDarkColorScheme else TraceLightColorScheme,
            typography = TraceTypography,
            shapes = CohesiveShapes,
            content = content,
        )
    }
}

/**
 * The spacing scale, read as `MaterialTheme.spacing.md`.
 *
 * Prefer this over literal dp values. A screen that invents its own padding is the first step
 * towards a screen that looks like it came from a different app.
 */
val MaterialTheme.spacing: TraceSpacing
    @Composable @ReadOnlyComposable get() = LocalTraceSpacing.current

/**
 * Observed rather than read once. Android does not deliver a configuration change when the
 * animation setting flips, so a plain read would leave a running app animating after the user
 * asked it to stop.
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

/**
 * Observed for the same reason as [rememberReducedMotion]: there is no configuration change when
 * power-save mode flips, and a user who enables it mid-session is asking the app to do less work
 * right now — not the next time it is launched.
 *
 * `RECEIVER_NOT_EXPORTED` is correct despite this being a system broadcast: system broadcasts are
 * exempt from the Android 13+ export requirement, and declaring the receiver unexported means no
 * other app can spoof it.
 */
@Composable
private fun rememberBatterySaver(): Boolean {
    val context = LocalContext.current
    val power = remember(context) {
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }
    var saving by remember(power) { mutableStateOf(power?.isPowerSaveMode == true) }
    DisposableEffect(power) {
        if (power == null) return@DisposableEffect onDispose { }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                saving = power.isPowerSaveMode
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return saving
}
