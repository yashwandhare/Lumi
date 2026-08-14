package com.trace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        setContent {
            TraceTheme {
                TraceApp()
            }
        }
    }
}
