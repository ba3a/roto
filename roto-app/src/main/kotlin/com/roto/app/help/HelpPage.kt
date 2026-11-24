package com.roto.app.help

import com.roto.app.Screen

/**
 * A help page: one Markdown file under `docs/help/`, bundled into the app and published on the site.
 *
 * @property id the file's name without `.md`.
 * @property title the page's title, which its level 1 heading repeats.
 */
enum class HelpPage(val id: String, val title: String) {
    /** The overview, with a link to every page. */
    INDEX("index", "Roto help"),

    /** The home window. */
    HOME("home", "Home"),

    /** Importing and trimming a video. */
    TRIM("trim", "Trim a video"),

    /** Importing a photo. */
    STILL("still", "Add a photo"),

    /** Clip setup: plate, mask, key frames and brush. */
    CLIP_SETUP("clip-setup", "Set up a clip"),

    /** Drawing: scale, zones and the draft. */
    DRAW("draw", "Draw a clip"),

    /** Handoff and publish. */
    HANDOFF("handoff", "Handoff and publish"),

    /** The project wizard. */
    WIZARD("wizard", "New project");

    /** The page's file name. */
    val file: String get() = "$id.md"

    /** Whether the page describes one screen, and so follows the screen layout and lists its controls. */
    val isScreenPage: Boolean get() = this != INDEX

    /**
     * Where a link leads: the page and the anchor it names, if any.
     *
     * @property page the page
     * @property anchor the heading slug on it, or null
     */
    data class Target(val page: HelpPage, val anchor: String?)

    /** Finding the page a link names. */
    companion object {
        /** The page a link [target] such as `draw.md#zones` names, or null when it names none. */
        fun resolve(target: String): Target? {
            val file = target.substringBefore('#')
            val page = entries.firstOrNull { it.file == file } ?: return null
            return Target(page, target.substringAfter('#', "").ifEmpty { null })
        }
    }
}

/** The help page that describes this screen. */
fun Screen.helpPage(): HelpPage =
    when (this) {
        Screen.Home -> HelpPage.HOME
        is Screen.Trimming -> HelpPage.TRIM
        is Screen.AddingStill -> HelpPage.STILL
        is Screen.SettingUp -> HelpPage.CLIP_SETUP
        is Screen.Drawing -> HelpPage.DRAW
        is Screen.HandingOff -> HelpPage.HANDOFF
        is Screen.Wizard -> HelpPage.WIZARD
    }
