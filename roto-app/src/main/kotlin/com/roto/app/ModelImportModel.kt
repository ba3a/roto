package com.roto.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.roto.core.model.AtlasModelSource
import com.roto.core.model.ConfirmedRow
import com.roto.core.model.ModelImporter
import com.roto.core.model.ModelRepository
import com.roto.core.model.ModelRow
import com.roto.core.model.ModelSource
import com.roto.core.model.ModelSourceKind
import com.roto.core.model.ModelSources
import com.roto.core.model.defaultAtlasPattern
import com.roto.core.profile.ProfileFolder
import com.roto.core.project.ProjectConfig
import java.nio.file.Path

/**
 * One row of the import dialog: an animation the source found and what the artist made of it.
 *
 * @property row the animation the source found
 * @property action the action it is filed under, or null until chosen
 * @property facing the facing it is filed under, or null until chosen
 * @property durationMs one duration for every frame, or null to keep the source's own
 * @property loop whether the animation repeats
 */
data class ModelImportRow(
    val row: ModelRow,
    val action: String?,
    val facing: String?,
    val durationMs: Int?,
    val loop: Boolean,
) {
    /** `<action>_<facing>`, or null while either is unchosen. */
    val key: String? get() = if (action == null || facing == null) null else "${action}_$facing"
}

/**
 * What the import dialog shows: the [source] picked and its [kind], the proposed [rows], the regions an
 * atlas [skipped], the atlas name [pattern], the [durationMs] given to frames when the source times none
 * itself, whether the artist agreed ([confirmedReplace]) to replace existing models, whether it is [importing], the
 * [imported] keys once done, and a [message] about the last thing that went wrong.
 *
 * @property source the file or folder picked
 * @property kind what it was detected as
 * @property rows one row per proposed model
 * @property skipped the atlas regions that matched the pattern for no action and facing
 * @property pattern the atlas name pattern
 * @property durationMs the duration in ms for frames of a source that gives none
 * @property confirmedReplace whether the artist agreed to replace models that already exist
 * @property importing whether the import is running
 * @property imported the keys of the models imported, once done
 * @property message the last thing that went wrong
 */
data class ModelImportState(
    val source: Path? = null,
    val kind: ModelSourceKind? = null,
    val rows: List<ModelImportRow> = emptyList(),
    val skipped: List<String> = emptyList(),
    val pattern: String,
    val durationMs: Int = DEFAULT_IMPORT_DURATION_MS,
    val confirmedReplace: Boolean = false,
    val importing: Boolean = false,
    val imported: List<String> = emptyList(),
    val message: String? = null,
)

/**
 * The import dialog's state and what can be done to it: pick a file or folder, see the models it
 * proposes, edit each row, then import. A row is valid when its action and facing are the project's, its
 * duration is positive and no other row has its key; a key that already exists needs the artist's
 * confirmation. Reading and importing run off the UI thread; every rule is `roto-core`'s.
 *
 * @property config the project, which names the allowed actions and facings
 * @property profileFolder the profile the models go into
 */
class ModelImportModel(private val config: ProjectConfig, private val profileFolder: ProfileFolder) {

    private val repository = ModelRepository(profileFolder)
    private var source: ModelSource? = null

    /** What the dialog shows. */
    var state by mutableStateOf(ModelImportState(pattern = defaultAtlasPattern(config.output)))
        private set

    /** The project's actions a row can take. */
    val actions: List<String> get() = config.actions

    /** The project's facings a row can take. */
    val facings: List<String> get() = config.facings

    /** Why [row] cannot be imported as it stands, or null when it can. */
    fun problem(row: ModelImportRow): String? = when {
        row.action == null -> "Choose an action"
        row.action !in config.actions -> "Unknown action '${row.action}'"
        row.facing == null -> "Choose a facing"
        row.facing !in config.facings -> "Unknown facing '${row.facing}'"
        row.durationMs != null && row.durationMs < 1 -> "A frame lasts at least 1 ms"
        state.rows.count { it.key == row.key } > 1 -> "Another row is also ${row.key}"
        else -> null
    }

    /** Whether [row] would replace a model that exists. */
    fun replaces(row: ModelImportRow): Boolean = row.key?.let(repository::exists) == true

    /** Whether any valid row would replace a model, so the artist has to confirm. */
    val needsConfirmation: Boolean get() = state.rows.any { problem(it) == null && replaces(it) }

    /** Whether the import can start. */
    val canImport: Boolean
        get() = !state.importing && state.rows.isNotEmpty() && state.rows.all { problem(it) == null } &&
            (!needsConfirmation || state.confirmedReplace)

    /** Reads [path]: detects the source and proposes its rows; edits to rows of the same id are kept. */
    suspend fun choose(path: Path) {
        state = state.copy(source = path, kind = null, rows = emptyList(), skipped = emptyList(), message = null)
        load(path, emptyMap())
    }

    /** Sets the atlas name pattern and re-reads an atlas with it. */
    suspend fun setPattern(pattern: String) {
        state = state.copy(pattern = pattern)
        reload()
    }

    /** Sets the duration for frames a source gives none and re-reads the source with it. */
    suspend fun setDuration(durationMs: Int) {
        state = state.copy(durationMs = durationMs)
        reload()
    }

    /** Files row [index] under [action]. */
    fun setAction(index: Int, action: String) = edit(index) { it.copy(action = action) }

    /** Files row [index] under [facing]. */
    fun setFacing(index: Int, facing: String) = edit(index) { it.copy(facing = facing) }

    /** Gives every frame of row [index] [durationMs]; null keeps the source's own. */
    fun setRowDuration(index: Int, durationMs: Int?) = edit(index) { it.copy(durationMs = durationMs) }

    /** Sets whether row [index] loops. */
    fun setLoop(index: Int, loop: Boolean) = edit(index) { it.copy(loop = loop) }

    /** Records that the artist agrees, or no longer agrees, to replace models that exist. */
    fun confirmReplace(confirmed: Boolean) {
        state = state.copy(confirmedReplace = confirmed)
    }

    /** Imports the rows: true once every model is in the profile. */
    suspend fun import(): Boolean {
        val origin = state.source ?: return false
        val current = source
        if (current == null || !canImport) return false
        state = state.copy(importing = true, message = null)
        val rows = state.rows
        val confirmed = rows.map { ConfirmedRow(it.row, it.action.orEmpty(), it.facing.orEmpty()) }
        val keys = reading {
            ModelImporter(profileFolder, config)
                .import(Edited(current, rows), confirmed, origin.fileName.toString(), state.confirmedReplace)
        }
        state = state.copy(importing = false, imported = keys ?: state.imported)
        return keys != null
    }

    private fun edit(index: Int, change: (ModelImportRow) -> ModelImportRow) {
        state = state.copy(
            rows = state.rows.mapIndexed { at, row -> if (at == index) change(row) else row },
            confirmedReplace = false,
        )
    }

    private suspend fun reload() {
        val path = state.source ?: return
        load(path, state.rows.associateBy { it.row.id })
    }

    private suspend fun load(path: Path, earlier: Map<String, ModelImportRow>) {
        val opened = reading {
            val opened = ModelSources.open(path, config, state.durationMs, state.pattern)
            val rows = opened.rows().map { it to opened.loop(it) }
            Opened(ModelSources.detect(path), opened, rows, (opened as? AtlasModelSource)?.skipped.orEmpty())
        } ?: return
        source = opened.source
        state = state.copy(
            kind = opened.kind,
            skipped = opened.skipped,
            confirmedReplace = false,
            rows = opened.rows.map { (row, loop) -> earlier[row.id] ?: proposed(row, loop) },
        )
    }

    private fun proposed(row: ModelRow, loop: Boolean) = ModelImportRow(
        row,
        row.action?.takeIf { it in config.actions },
        row.facing?.takeIf { it in config.facings },
        null,
        loop,
    )

    private suspend fun <T : Any> reading(work: () -> T): T? =
        offUiThread({ state = state.copy(message = it) }, work)

    private class Opened(
        val kind: ModelSourceKind,
        val source: ModelSource,
        val rows: List<Pair<ModelRow, Boolean>>,
        val skipped: List<String>,
    )

    /** [source] with the artist's loop and duration edits laid over it. */
    private class Edited(private val source: ModelSource, private val edits: List<ModelImportRow>) :
        ModelSource by source {
        override fun durations(row: ModelRow): List<Int> =
            edit(row)?.durationMs?.let { List(row.frameCount) { _ -> it } } ?: source.durations(row)

        override fun loop(row: ModelRow): Boolean = edit(row)?.loop ?: source.loop(row)

        private fun edit(row: ModelRow) = edits.firstOrNull { it.row.id == row.id }
    }
}

/** The duration in ms the dialog starts with for frames a source gives none. */
const val DEFAULT_IMPORT_DURATION_MS = 100
