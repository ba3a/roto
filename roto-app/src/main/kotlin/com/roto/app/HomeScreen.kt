package com.roto.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.roto.core.model.ModelClip
import com.roto.core.profile.Clip
import com.roto.core.profile.Profile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PANE_PADDING = 16.dp
private val LIST_WIDTH = 300.dp
private val MODEL_CARD_WIDTH = 152.dp
private val SETTINGS_WIDTH = 560.dp

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
    val overview: OverviewModel?,
)

/** What can be done in the settings window. */
private class SettingsActions(val onChooseLibrary: () -> Unit, val onChooseEditor: () -> Unit, val onClose: () -> Unit)

/** The home window: the project's profiles, the selected one beside them, and the settings window when it is open. */
@Composable
fun HomeScreen(model: HomeModel, pickers: Pickers) {
    val state = model.state
    val scope = rememberCoroutineScope()
    val screens = remember(model, pickers, scope) {
        object : OverviewActions {
            // The trim screen asks for the action and facing, so the cell's own are not used here.
            override fun importClip(action: String, facing: String) {
                pickers.video("Import video")?.let { scope.launch { model.importVideo(it) } }
            }

            override fun importModel() = model.importModel()

            override fun setUp(clip: Clip) = model.setUp(clip)

            override fun handOff() = model.handOff()
        }
    }
    val overview = remember(state.selectedEntry, state.models, state.project) { model.overview(screens) }
    Column(Modifier.fillMaxSize()) {
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
                    overview = overview,
                ),
                Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
    if (state.settingsOpen) {
        SettingsDialog(
            state,
            SettingsActions(
                onChooseLibrary = {
                    pickers.folder("Choose library", PickerCategory.LIBRARY, state.library)?.let(model::chooseLibrary)
                },
                onChooseEditor = {
                    pickers.program("Choose editor", state.settings.editorProgram)?.let(model::chooseEditor)
                },
                onClose = model::closeSettings,
            ),
        )
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
        val list = rememberLazyListState()
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), state = list) {
                items(state.profiles, key = { it.id }) { entry ->
                    ProfileRow(entry, selected = entry.id == state.selected, onClick = { model.select(entry.id) })
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
        if (state.project != null) {
            NewProfile(model)
            Helped(HelpControl.NEW_VARIANT) {
                OutlinedButton(onClick = model::startVariant, Modifier.padding(horizontal = PANE_PADDING)) {
                    Text("New variant of…")
                }
            }
        }
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
        Box(Modifier.weight(1f)) {
            Helped(HelpControl.NEW_PROFILE) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("New profile") },
                )
            }
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
    val scroll = rememberScrollState()
    Box(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(PANE_PADDING)) { DetailContent(state, actions) }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

/** The selected profile's summary, a hint when none is selected, or why it cannot be read. */
@Composable
private fun DetailContent(state: HomeState, actions: ProfileActions) {
    val entry = state.selectedEntry
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

@Composable
private fun ProfileSummary(id: String, profile: Profile, models: List<ModelEntry>, actions: ProfileActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(id, style = MaterialTheme.typography.headlineSmall)
        Text("Ramps: ${profile.ramps.ifEmpty { listOf("none chosen") }.joinToString()}")
        Text(profile.scale?.let { "Scale: ${it.bodyHeight} px tall" } ?: "Scale: not marked")
        actions.overview?.let { OverviewScreen(it) }
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

/** The profile's models, each a card with its preview and a button to remove it, and the button that imports more. */
@Composable
private fun ModelList(models: List<ModelEntry>, actions: ProfileActions) {
    var removing by remember { mutableStateOf<String?>(null) }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Models: ${models.size}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Helped(HelpControl.IMPORT_MODEL) { OutlinedButton(onClick = actions.models.onImport) { Text("Import model…") } }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        models.forEach { entry -> ModelCard(entry, onRemove = { removing = entry.key }) }
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

/** One model: its first frame, playing while the pointer rests on it, its key, and the button that removes it. */
@Composable
private fun ModelCard(entry: ModelEntry, onRemove: () -> Unit) {
    OutlinedCard(Modifier.width(MODEL_CARD_WIDTH)) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val clip = entry.clip
            val square = Modifier.fillMaxWidth().aspectRatio(1f)
            Helped(HelpControl.MODEL_PREVIEW) {
                if (clip != null) {
                    ModelPreview(clip, square)
                } else {
                    val background = MaterialTheme.colorScheme.surfaceVariant
                    Box(square.background(background), contentAlignment = Alignment.Center) {
                        Text("Cannot be read", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(entry.key, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            entry.problem?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Helped(HelpControl.REMOVE_MODEL) { TextButton(onClick = onRemove) { Text("Remove model…") } }
        }
    }
}

/**
 * [clip]'s first frame, its pixels square. While the pointer rests on it, it plays at the model's own
 * durations: round and round when the model loops, once and then holding the last frame when it does not.
 */
@Composable
private fun ModelPreview(clip: ModelClip, modifier: Modifier) {
    val bitmaps = remember(clip) { clip.frames.map { it.raster.toImageBitmap() } }
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    var index by remember(clip) { mutableIntStateOf(0) }
    LaunchedEffect(clip, hovered) {
        index = 0
        while (hovered && (clip.loop || index < clip.frames.lastIndex)) {
            delay(clip.frames[index].durationMs.toLong())
            index = (index + 1) % clip.frames.size
        }
    }
    Canvas(modifier.background(MaterialTheme.colorScheme.surfaceVariant).hoverable(hover)) {
        val image = bitmaps[index]
        drawPixels(image, Fit.wholePixels(size.width.toInt(), size.height.toInt(), image.width, image.height))
    }
}

/** Where the project is, where its profiles are read from, and the editor, with the buttons that change them. */
@Composable
private fun SettingsDialog(state: HomeState, actions: SettingsActions) {
    AlertDialog(
        onDismissRequest = actions.onClose,
        title = { Text("Settings") },
        text = {
            Column(Modifier.widthIn(min = SETTINGS_WIDTH), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Setting("Project", state.project?.let { "${it.config.id} — ${it.root}" } ?: "None open")
                val library = state.library?.let { if (state.settings.library == null) "$it (default)" else "$it" }
                Setting("Library", library.orEmpty()) {
                    Helped(HelpControl.CHOOSE_LIBRARY) {
                        OutlinedButton(onClick = actions.onChooseLibrary) { Text("Choose library…") }
                    }
                }
                state.profilesFolder?.let { folder ->
                    val found = state.profilesFolderFound
                    Setting("Profiles", if (found) "$folder" else "$folder — not found", problem = !found)
                }
                Setting("Editor", state.settings.editor ?: "The system's default") {
                    Helped(HelpControl.CHOOSE_EDITOR) {
                        OutlinedButton(onClick = actions.onChooseEditor) { Text("Choose editor…") }
                    }
                }
            }
        },
        confirmButton = {
            Helped(HelpControl.SETTINGS_CLOSE) { TextButton(onClick = actions.onClose) { Text("Close") } }
        },
    )
}

/** One setting: its [label], its [value] (in the error colour when it is a [problem]) and the button that changes it. */
@Composable
private fun Setting(label: String, value: String, problem: Boolean = false, action: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge)
        SelectionContainer(Modifier.weight(1f)) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (problem) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        }
        Spacer(Modifier.width(8.dp))
        action()
    }
}
