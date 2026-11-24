package com.roto.app.help

import java.io.IOException

private const val RESOURCE_FOLDER = "/help/"

/**
 * The help pages, each parsed on first use. [source] gives a page's Markdown, so a test can stand in
 * for the bundled files.
 */
class HelpLibrary(private val source: (HelpPage) -> String) {
    private val documents = HelpPage.entries.associateWith { page -> lazy { HelpDocument.parse(source(page)) } }

    /** The parsed [page]. */
    fun document(page: HelpPage): HelpDocument = documents.getValue(page).value

    /**
     * The tooltip for [control]: the first sentence of its row. Null when the page cannot be read or
     * lacks the row, so a defect in a page never breaks the screen it describes; the coverage test is
     * what catches it.
     */
    fun tooltip(control: HelpControl): String? =
        try {
            document(control.page).summary(control.label)
        } catch (_: IOException) {
            null
        } catch (_: HelpFormatException) {
            null
        }

    /** What the library is made of. */
    companion object {
        /** The pages bundled in the app, from `docs/help/` through the resources. */
        fun bundled(): HelpLibrary = HelpLibrary { page ->
            val stream = HelpLibrary::class.java.getResourceAsStream(RESOURCE_FOLDER + page.file)
                ?: throw IOException("Help page ${page.file} is not bundled")
            stream.use { String(it.readBytes(), Charsets.UTF_8) }
        }
    }
}
