package com.trace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.trace.ui.TraceApp
import com.trace.ui.theme.TraceTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The only activity.
 *
 * Voice sessions and the SOS screen render as dialogs and destinations inside this activity
 * rather than as separate activities, so they share one resident model session. v1 learned
 * this the hard way: a second activity meant a second view model and a second attempt to
 * hold the model.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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
                TraceApp(
                    isDarkTheme = isDarkTheme,
                    onThemeToggle = { 
                        val newValue = !isDarkTheme
                        userThemeOverride = newValue
                        sharedPref.edit().putInt("theme_mode", if (newValue) 1 else 0).apply()
                    }
                )
            }
        }
    }
}
