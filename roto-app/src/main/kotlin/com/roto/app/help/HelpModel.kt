package com.roto.app.help

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the help pane shows.
 *
 * @property open whether the pane is showing.
 * @property page the page it shows.
 * @property anchor the heading on that page to scroll to, if a link named one.
 */
data class HelpState(
    val open: Boolean = false,
    val page: HelpPage = HelpPage.INDEX,
    val anchor: String? = null,
)

/**
 * The help pane's state and actions: plain Kotlin, tested without a window.
 *
 * @property library the pages the pane shows
 */
class HelpModel(val library: HelpLibrary, start: HelpPage = HelpPage.HOME) {
    /** What the pane shows now. */
    var state by mutableStateOf(HelpState(page = start))
        private set

    /** Shows or hides the pane. */
    fun toggle() {
        state = state.copy(open = !state.open)
    }

    /** Hides the pane. */
    fun close() {
        state = state.copy(open = false)
    }

    /** The window now shows the screen that [page] describes; the pane follows it. */
    fun screenChanged(page: HelpPage) {
        state = state.copy(page = page, anchor = null)
    }

    /** Shows [page], at [anchor] if given. */
    fun show(page: HelpPage, anchor: String? = null) {
        state = state.copy(page = page, anchor = anchor)
    }

    /** Follows a link [target] to another page; false when it names no page. */
    fun follow(target: String): Boolean {
        val resolved = HelpPage.resolve(target) ?: return false
        show(resolved.page, resolved.anchor)
        return true
    }
}
