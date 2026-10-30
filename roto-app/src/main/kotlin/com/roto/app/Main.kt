package com.roto.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.roto.core.settings.SettingsStore
import javax.swing.UIManager

fun main() {
    // The folder chooser is Swing's; the system look makes it the platform's familiar dialog.
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    val model = HomeModel(SettingsStore.default())
    application {
        val state = rememberWindowState(size = DpSize(1100.dp, 720.dp))
        val title = model.state.project?.let { "Roto — ${it.config.id}" } ?: "Roto"
        Window(onCloseRequest = ::exitApplication, state = state, title = title) {
            val pickers = remember { SwingPickers(window) }
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (val screen = model.screen) {
                        Screen.Home -> HomeScreen(model, pickers)
                        is Screen.Trimming -> TrimScreen(screen.model, model::closeClip)
                        is Screen.SettingUp -> ClipSetupScreen(screen.model, model::closeClip)
                        is Screen.Drawing -> DrawScreen(screen.model, model::closeClip)
                        is Screen.HandingOff -> HandoffScreen(screen.model, model::closeClip)
                    }
                }
            }
        }
    }
}
