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
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.roto.core.profile.Clip
import com.roto.core.profile.Profile
import kotlinx.coroutines.launch

private val PANE_PADDING = 16.dp
private val LIST_WIDTH = 300.dp

/** What can be done to the selected profile and its clips. */
private class ProfileActions(
    val onImport: () -> Unit,
    val onHandOff: () -> Unit,
    val onTrim: (Clip) -> Unit,
    val onSetUp: (Clip) -> Unit,
    val onDraw: (Clip) -> Unit,
)

/** The home window: the places Roto works with at the top, the project's profiles below, the selected one beside them. */
@Composable
fun HomeScreen(model: HomeModel, pickers: Pickers) {
    val state = model.state
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        PlacesBar(
            state,
            onOpenProject = { pickers.folder("Open project", state.settings.projectFolder)?.let(model::openProject) },
            onChooseLibrary = {
                pickers.folder("Choose library", state.settings.libraryFolder)?.let(model::chooseLibrary)
            },
            onChooseEditor = {
                pickers.program("Choose editor", state.settings.editorProgram)?.let(model::chooseEditor)
            },
        )
        HorizontalDivider()
        state.message?.let { MessageBar(it, model::dismissMessage) }
        Row(Modifier.fillMaxSize()) {
            ProfilePane(state, model, Modifier.width(LIST_WIDTH).fillMaxHeight())
            VerticalDivider()
            ProfileDetail(
                state,
                ProfileActions(
                    onImport = { pickers.video("Import video")?.let { scope.launch { model.importVideo(it) } } },
                    onHandOff = model::handOff,
                    onTrim = { clip -> scope.launch { model.retrim(clip) } },
                    onSetUp = model::setUp,
                    onDraw = model::draw,
                ),
                Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

/** What Roto works with: the open project, the library folder and the editor, each with its button. */
@Composable
private fun PlacesBar(
    state: HomeState,
    onOpenProject: () -> Unit,
    onChooseLibrary: () -> Unit,
    onChooseEditor: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(PANE_PADDING), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Place(
                label = "Project",
                value = state.project?.let { "${it.config.id} — ${it.root}" } ?: "None open",
                action = "Open project…",
                onClick = onOpenProject,
            )
            Place(
                label = "Library",
                value = state.settings.library ?: "Not chosen",
                action = "Choose library…",
                onClick = onChooseLibrary,
            )
            Place(
                label = "Editor",
                value = state.settings.editor ?: "The system's default",
                action = "Choose editor…",
                onClick = onChooseEditor,
            )
        }
    }
}

@Composable
private fun Place(label: String, value: String, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge)
        Text(
            value,
            Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun MessageBar(message: String, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = PANE_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
        TextButton(onClick = onDismiss) { Text("Dismiss") }
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
            state.settings.library == null -> "Choose the library folder Roto keeps profiles in. It is asked for once."
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
        if (state.project != null && state.settings.library != null) NewProfile(model)
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
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("New profile") },
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = { if (model.createProfile(name)) name = "" }, enabled = name.isNotBlank()) { Text("Create") }
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
                Text(entry.problem.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
            else -> ProfileSummary(entry.id, entry.profile, actions)
        }
    }
}

@Composable
private fun ProfileSummary(id: String, profile: Profile, actions: ProfileActions) {
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
            OutlinedButton(onClick = actions.onImport) { Text("Import video…") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = actions.onHandOff, enabled = profile.clips.isNotEmpty()) { Text("Handoff and publish…") }
        }
        profile.clips.forEach { clip -> ClipRow(clip, actions) }
    }
}

@Composable
private fun ClipRow(clip: Clip, actions: ProfileActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ClipSummary(clip, Modifier.weight(1f))
        TextButton(onClick = { actions.onTrim(clip) }) { Text("Trim…") }
        TextButton(onClick = { actions.onSetUp(clip) }) { Text("Set up…") }
        TextButton(onClick = { actions.onDraw(clip) }) { Text("Draw…") }
    }
}

@Composable
private fun ClipSummary(clip: Clip, modifier: Modifier) {
    Column(modifier) {
        Text("${clip.action} · ${clip.facing}", style = MaterialTheme.typography.bodyLarge)
        Text(
            "${clip.source}, ${seconds(clip.trim.startMs)} to ${seconds(clip.trim.endMs)}" +
                (clip.markers?.let { ", ${it.size - 1} key frames marked" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
