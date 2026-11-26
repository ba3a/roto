package com.roto.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.roto.app.help.HelpControl
import com.roto.app.help.Helped
import com.roto.core.profile.Clip
import com.roto.core.profile.Profile
import kotlinx.coroutines.launch
import java.nio.file.Path

private val PANE_PADDING = 16.dp
private val LIST_WIDTH = 300.dp

/** What can be done to the selected profile's models. */
private class ModelActions(val onImport: () -> Unit, val onRemove: (String) -> Unit)

/** What can be done to one clip. */
private class ClipActions(val onTrim: (Clip) -> Unit, val onSetUp: (Clip) -> Unit, val onDraw: (Clip) -> Unit)

/** What can be done to the selected profile and its clips. */
private class ProfileActions(
    val onImportVideo: () -> Unit,
    val onImportPhoto: () -> Unit,
    val models: ModelActions,
    val onHandOff: () -> Unit,
    val clips: ClipActions,
)

/** What can be done to the project: make, open, reopen, and share its setup. */
private class ProjectActions(
    val onNewProject: () -> Unit,
    val onOpenProject: () -> Unit,
    val onOpenRecent: (Path) -> Unit,
    val onImportBundle: () -> Unit,
    val onExportPreset: () -> Unit,
)

/** What can be done to the places Roto works with. */
private class PlacesActions(
    val project: ProjectActions,
    val onChooseLibrary: () -> Unit,
    val onChooseEditor: () -> Unit,
)

/** The home window: the places Roto works with at the top, the project's profiles below, the selected one beside them. */
@Composable
fun HomeScreen(model: HomeModel, pickers: Pickers) {
    val state = model.state
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        PlacesBar(
            state,
            PlacesActions(
                ProjectActions(
                    onNewProject = model::startWizard,
                    onOpenProject = {
                        pickers.folder("Open project", PickerCategory.PROJECT, state.settings.projectFolder)
                            ?.let(model::openProject)
                    },
                    onOpenRecent = model::openProject,
                    onImportBundle = { pickers.presetBundle("Import preset")?.let(model::importBundle) },
                    onExportPreset = {
                        state.project?.let { project ->
                            pickers.savePresetBundle("Export as preset", project.config.id)
                                ?.let { scope.launch { model.exportPreset(it) } }
                        }
                    },
                ),
                onChooseLibrary = {
                    pickers.folder("Choose library", PickerCategory.LIBRARY, state.library)?.let(model::chooseLibrary)
                },
                onChooseEditor = {
                    pickers.program("Choose editor", state.settings.editorProgram)?.let(model::chooseEditor)
                },
            ),
        )
        HorizontalDivider()
        state.message?.let { MessageBar(it, model::dismissMessage) }
        Row(Modifier.fillMaxSize()) {
            ProfilePane(state, model, Modifier.width(LIST_WIDTH).fillMaxHeight())
            VerticalDivider()
            ProfileDetail(
                state,
                ProfileActions(
                    onImportVideo = { pickers.video("Import video")?.let { scope.launch { model.importVideo(it) } } },
                    onImportPhoto = { pickers.photo("Import photo")?.let(model::importPhoto) },
                    models = ModelActions(model::importModel, model::removeModel),
                    onHandOff = model::handOff,
                    clips = ClipActions({ clip -> scope.launch { model.retrim(clip) } }, model::setUp, model::draw),
                ),
                Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

/** The open project and the buttons that make, open and share projects. */
@Composable
private fun ProjectRow(state: HomeState, actions: ProjectActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Project", Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge)
        Text(
            state.project?.let { "${it.config.id} — ${it.root}" } ?: "None open",
            Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        Helped(HelpControl.NEW_PROJECT) { Button(onClick = actions.onNewProject) { Text("New project…") } }
        Spacer(Modifier.width(8.dp))
        Helped(HelpControl.OPEN_FOLDER) {
            OutlinedButton(onClick = actions.onOpenProject) { Text("Open folder…") }
        }
        Spacer(Modifier.width(8.dp))
        RecentMenu(state, actions.onOpenRecent)
        Spacer(Modifier.width(8.dp))
        Helped(HelpControl.IMPORT_PRESET) {
            OutlinedButton(onClick = actions.onImportBundle) { Text("Import preset…") }
        }
        Spacer(Modifier.width(8.dp))
        Helped(HelpControl.EXPORT_PRESET) {
            OutlinedButton(onClick = actions.onExportPreset, enabled = state.project != null) {
                Text("Export as preset…")
            }
        }
    }
}

/** What Roto works with: the open project, the library folder and the editor, each with its button. */
@Composable
private fun PlacesBar(state: HomeState, actions: PlacesActions) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(PANE_PADDING), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProjectRow(state, actions.project)
            Place(
                label = "Library",
                value = state.library?.let { if (state.settings.library == null) "$it (default)" else "$it" } ?: "",
                action = "Choose library…",
                help = HelpControl.CHOOSE_LIBRARY,
                onClick = actions.onChooseLibrary,
            )
            ProfilesFolderRow(state)
            Place(
                label = "Editor",
                value = state.settings.editor ?: "The system's default",
                action = "Choose editor…",
                help = HelpControl.CHOOSE_EDITOR,
                onClick = actions.onChooseEditor,
            )
        }
    }
}

/** The exact folder the profiles are read from, in the error colour when it does not exist. */
@Composable
private fun ProfilesFolderRow(state: HomeState) {
    val folder = state.profilesFolder ?: return
    val found = state.profilesFolderFound
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Profiles", Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge)
        Text(
            if (found) "$folder" else "$folder — not found",
            Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = if (found) Color.Unspecified else MaterialTheme.colorScheme.error,
        )
    }
}

/** A button listing the projects opened before, apart from the open one; choosing one reopens it with its library. */
@Composable
private fun RecentMenu(state: HomeState, onOpen: (Path) -> Unit) {
    val open = state.project?.root?.toString()
    val others = state.settings.recent.filter { it.project != open }
    var expanded by remember { mutableStateOf(false) }
    Box {
        Helped(HelpControl.RECENT_PROJECTS) {
            OutlinedButton(onClick = { expanded = true }, enabled = others.isNotEmpty()) { Text("Recent…") }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            others.forEach { recent ->
                val folder = Path.of(recent.project)
                DropdownMenuItem(
                    text = { Text("${folder.fileName ?: folder} — $folder") },
                    onClick = {
                        expanded = false
                        onOpen(folder)
                    },
                )
            }
        }
    }
}

@Composable
private fun Place(label: String, value: String, action: String, help: HelpControl, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge)
        Text(
            value,
            Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        Helped(help) { OutlinedButton(onClick = onClick) { Text(action) } }
    }
}

@Composable
private fun MessageBar(message: String, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = PANE_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectableMessage(message, Modifier.weight(1f), MaterialTheme.colorScheme.onErrorContainer)
        Helped(HelpControl.DISMISS) { TextButton(onClick = onDismiss) { Text("Dismiss") } }
    }
}

/** The project's profiles, and a field to name a new one. */
@Composable
private fun ProfilePane(state: HomeState, model: HomeModel, modifier: Modifier) {
    Column(modifier.padding(vertical = PANE_PADDING)) {
        Text("Profiles", Modifier.padding(horizontal = PANE_PADDING), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        val hint = when {
            state.project == null -> "Open the project folder that holds roto.json."
            state.profiles.isEmpty() && !state.profilesFolderFound ->
                "No profiles folder yet. Name a profile below to make it, or choose the library " +
                    "that holds your profiles."
            state.profiles.isEmpty() -> "No profiles yet. Name one below."
            else -> null
        }
        if (hint != null) {
            Text(
                hint,
                Modifier.padding(horizontal = PANE_PADDING),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(state.profiles, key = { it.id }) { entry ->
                ProfileRow(entry, selected = entry.id == state.selected, onClick = { model.select(entry.id) })
            }
        }
        if (state.project != null) NewProfile(model)
    }
}

@Composable
private fun ProfileRow(entry: ProfileEntry, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Column(
        Modifier.fillMaxWidth().background(background).clickable(onClick = onClick)
            .padding(horizontal = PANE_PADDING, vertical = 10.dp),
    ) {
        Text(entry.id, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        val summary = entry.profile?.let { clipCount(it.clips.size) } ?: "Cannot be read"
        val colours = MaterialTheme.colorScheme
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = if (entry.problem != null) colours.error else colours.onSurfaceVariant,
        )
    }
}

@Composable
private fun NewProfile(model: HomeModel) {
    var name by remember { mutableStateOf("") }
    Row(Modifier.padding(horizontal = PANE_PADDING), verticalAlignment = Alignment.CenterVertically) {
        Helped(HelpControl.NEW_PROFILE) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("New profile") },
            )
        }
        Spacer(Modifier.width(8.dp))
        Helped(HelpControl.CREATE_PROFILE) {
            Button(
                onClick = { if (model.createProfile(name)) name = "" },
                enabled = name.isNotBlank(),
            ) { Text("Create") }
        }
    }
}

/** The selected profile: its ramps, scale and clips, or why it cannot be read. */
@Composable
private fun ProfileDetail(state: HomeState, actions: ProfileActions, modifier: Modifier) {
    val entry = state.selectedEntry
    Box(modifier.padding(PANE_PADDING)) {
        when {
            entry == null && state.profiles.isEmpty() -> Unit
            entry == null -> Text(
                "Select a profile to see its clips.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entry.profile == null -> Column {
                Text(entry.id, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                SelectableMessage(entry.problem.orEmpty())
            }
            else -> ProfileSummary(entry.id, entry.profile, state.models, actions)
        }
    }
}

@Composable
private fun ProfileSummary(id: String, profile: Profile, models: List<String>, actions: ProfileActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(id, style = MaterialTheme.typography.headlineSmall)
        Text("Ramps: ${profile.ramps.ifEmpty { listOf("none chosen") }.joinToString()}")
        Text(profile.scale?.let { "Scale: ${it.bodyHeight} px tall" } ?: "Scale: not marked")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                clipCount(profile.clips.size),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            Helped(HelpControl.IMPORT_VIDEO) {
                OutlinedButton(onClick = actions.onImportVideo) { Text("Import video…") }
            }
            Spacer(Modifier.width(8.dp))
            Helped(HelpControl.IMPORT_PHOTO) {
                OutlinedButton(onClick = actions.onImportPhoto) { Text("Import photo…") }
            }
            Spacer(Modifier.width(8.dp))
            Helped(HelpControl.HAND_OFF) {
                Button(onClick = actions.onHandOff, enabled = profile.clips.isNotEmpty()) {
                    Text("Handoff and publish…")
                }
            }
        }
        profile.clips.forEach { clip -> ClipRow(clip, actions) }
        ModelList(models, actions)
    }
}

@Composable
private fun ClipRow(clip: Clip, actions: ProfileActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ClipSummary(clip, Modifier.weight(1f))
        if (!clip.isStill) {
            Helped(HelpControl.CLIP_TRIM) { TextButton(onClick = { actions.clips.onTrim(clip) }) { Text("Trim…") } }
        }
        Helped(HelpControl.CLIP_SET_UP) { TextButton(onClick = { actions.clips.onSetUp(clip) }) { Text("Set up…") } }
        Helped(HelpControl.CLIP_DRAW) { TextButton(onClick = { actions.clips.onDraw(clip) }) { Text("Draw…") } }
    }
}

@Composable
private fun ClipSummary(clip: Clip, modifier: Modifier) {
    val cut = clip.trim?.let { "${seconds(it.startMs)} to ${seconds(it.endMs)}" } ?: "still"
    Column(modifier) {
        Text("${clip.action} · ${clip.facing}", style = MaterialTheme.typography.bodyLarge)
        Text(
            "${clip.source}, $cut" + (clip.markers?.let { ", ${it.size - 1} key frames marked" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The profile's models, each with a button to remove it, and the button that imports more. */
@Composable
private fun ModelList(models: List<String>, actions: ProfileActions) {
    var removing by remember { mutableStateOf<String?>(null) }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Models: ${models.size}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Helped(HelpControl.IMPORT_MODEL) { OutlinedButton(onClick = actions.models.onImport) { Text("Import model…") } }
    }
    models.forEach { key ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(key, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Helped(HelpControl.REMOVE_MODEL) { TextButton(onClick = { removing = key }) { Text("Remove model…") } }
        }
    }
    removing?.let { key ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove $key?") },
            text = { Text("The model's frames are deleted from the profile. This cannot be undone.") },
            confirmButton = {
                Helped(HelpControl.REMOVE_MODEL_CONFIRM) {
                    TextButton(onClick = {
                        removing = null
                        actions.models.onRemove(key)
                    }) { Text("Remove") }
                }
            },
            dismissButton = {
                Helped(HelpControl.REMOVE_MODEL_KEEP) { TextButton(onClick = { removing = null }) { Text("Keep") } }
            },
        )
    }
}
