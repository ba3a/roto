package com.roto.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import com.roto.app.help.HelpModel
import com.roto.app.help.HelpPage
import kotlinx.coroutines.launch
import java.nio.file.Path

/**
 * The window's menus. File makes, opens, reopens and shares projects, opens the settings window and
 * exits; every item but Exit is off while a screen other than home fills the window, so nothing it does
 * can leave a clip half edited. Help shows the [help] pane, on the current screen's page or on the index.
 */
@Composable
fun FrameWindowScope.AppMenuBar(model: HomeModel, pickers: Pickers, help: HelpModel, onExit: () -> Unit) {
    val state = model.state
    val home = model.screen == Screen.Home
    val scope = rememberCoroutineScope()
    val open = state.project?.root?.toString()
    val recent = state.settings.recent.filter { it.project != open }.map { Path.of(it.project) }
    MenuBar {
        Menu("File", mnemonic = 'F') {
            Item("New project…", enabled = home, mnemonic = 'N', shortcut = KeyShortcut(Key.N, ctrl = true)) {
                model.startWizard()
            }
            Item("Open folder…", enabled = home, mnemonic = 'O', shortcut = KeyShortcut(Key.O, ctrl = true)) {
                pickers.folder("Open project", PickerCategory.PROJECT, state.settings.projectFolder)
                    ?.let(model::openProject)
            }
            Menu("Open recent", enabled = home && recent.isNotEmpty(), mnemonic = 'R') {
                recent.forEach { folder ->
                    Item("${folder.fileName ?: folder} — $folder") { model.openProject(folder) }
                }
            }
            Separator()
            Item("Import preset…", enabled = home, mnemonic = 'I') {
                pickers.presetBundle("Import preset")?.let(model::importBundle)
            }
            Item("Export as preset…", enabled = home && state.project != null, mnemonic = 'E') {
                state.project?.let { project ->
                    pickers.savePresetBundle("Export as preset", project.config.id)
                        ?.let { scope.launch { model.exportPreset(it) } }
                }
            }
            Separator()
            Item(
                "Settings…",
                enabled = home,
                mnemonic = 'S',
                shortcut = KeyShortcut(Key.Comma, ctrl = true),
                onClick = model::openSettings,
            )
            Separator()
            Item("Exit", mnemonic = 'x', onClick = onExit)
        }
        Menu("Help", mnemonic = 'H') {
            // F1 is handled by the window itself, so it works whatever has focus; the label only names it.
            CheckboxItem(
                "Help pane (F1)",
                checked = help.state.open,
                mnemonic = 'P',
                onCheckedChange = { help.toggle() },
            )
            Item("All help topics", mnemonic = 'A') { help.open(HelpPage.INDEX) }
        }
    }
}
