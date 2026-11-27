package com.roto.app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.roto.app.help.HelpLibrary
import com.roto.app.help.HelpModel
import com.roto.app.help.HelpPane
import com.roto.app.help.LocalHelp
import com.roto.app.help.helpPage
import com.roto.core.settings.SettingsStore
import javax.swing.UIManager

/** Opens the Roto window. */
fun main() {
    // The folder chooser is Swing's; the system look makes it the platform's familiar dialog.
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    val model = HomeModel(SettingsStore.default())
    val library = HelpLibrary.bundled()
    val help = HelpModel(library)
    application {
        val state = rememberWindowState(size = DpSize(1100.dp, 720.dp))
        val title = model.state.project?.let { "Roto — ${it.config.id}" } ?: "Roto"
        Window(
            onCloseRequest = ::exitApplication,
            state = state,
            title = title,
            onPreviewKeyEvent = { event ->
                val toggle = event.key == Key.F1 && event.type == KeyEventType.KeyDown
                if (toggle) help.toggle()
                toggle
            },
        ) {
            val pickers = remember { SwingPickers(window, model) }
            AppMenuBar(model, pickers, help, onExit = ::exitApplication)
            LaunchedEffect(model.screen) { help.screenChanged(model.screen.helpPage()) }
            MaterialTheme {
                CompositionLocalProvider(LocalHelp provides library) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxSize()) {
                            Surface(Modifier.weight(1f).fillMaxHeight()) { CurrentScreen(model, pickers) }
                            if (help.state.open) {
                                VerticalDivider()
                                HelpPane(help)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The screen the model is on. */
@Composable
private fun CurrentScreen(model: HomeModel, pickers: Pickers) {
    when (val screen = model.screen) {
        Screen.Home -> HomeScreen(model, pickers)
        is Screen.Trimming -> TrimScreen(screen.model, model::closeClip)
        is Screen.AddingStill -> StillScreen(screen.model, pickers, model::closeClip)
        is Screen.ImportingModel -> ModelImportScreen(screen.model, pickers, model::closeClip)
        is Screen.SettingUp -> ClipSetupScreen(screen.model, pickers, model::closeClip)
        is Screen.Drawing -> DrawScreen(screen.model, model::closeClip)
        is Screen.HandingOff -> HandoffScreen(screen.model, model::closeClip)
        is Screen.Wizard -> WizardScreen(screen.model, pickers, model::closeClip, model::finishWizard)
        is Screen.MakingVariant -> VariantScreen(screen.model, model::closeClip, model::finishVariant)
    }
}
