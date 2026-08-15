package com.trace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trace.core.ai.ModelHarness
import com.trace.core.ai.ModelState
import com.trace.ui.TraceApp
import com.trace.ui.onboarding.ModelSetupScreen
import com.trace.ui.theme.TraceTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The only activity.
 *
 * Voice sessions and the SOS screen render as dialogs and destinations inside this activity
 * rather than as separate activities, so they share one resident model session. v1 learned
 * this the hard way: a second activity meant a second view model and a second attempt to
 * hold the model.
 *
 * The model gate lives here, above [TraceApp] and its NavHost, so the drawer and its routes
 * never appear before the model exists. First run shows [ModelSetupScreen]; once the harness
 * reaches [ModelState.Ready] the app takes over for the rest of the process. A returning user
 * whose model is already on disk passes through this screen in a moment — it loads, not
 * downloads — so the gate does not become a stop sign for them.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var harness: ModelHarness

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("trace_settings", android.content.Context.MODE_PRIVATE)

        setContent {
            val systemTheme = androidx.compose.foundation.isSystemInDarkTheme()
            var userThemeOverride by androidx.compose.runtime.remember {
                val saved = sharedPref.getInt("theme_mode", -1)
                androidx.compose.runtime.mutableStateOf(if (saved == -1) null else saved == 1)
            }

            val isDarkTheme = userThemeOverride ?: systemTheme

            TraceTheme(darkTheme = isDarkTheme) {
                val modelState by harness.state.collectAsStateWithLifecycle()
                if (modelState is ModelState.Ready) {
                    TraceApp(
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = {
                            val newValue = !isDarkTheme
                            userThemeOverride = newValue
                            sharedPref.edit().putInt("theme_mode", if (newValue) 1 else 0).apply()
                        }
                    )
                } else {
                    ModelSetupScreen(
                        onReady = { /* state drives the gate; nothing to navigate here */ },
                    )
                }
            }
        }
    }
}
