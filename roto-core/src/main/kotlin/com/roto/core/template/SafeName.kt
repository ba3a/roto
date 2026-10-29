package com.roto.core.template

/**
 * The rule for every name that ends up in a file name — project ids, profile ids, facings, actions:
 * it starts with a letter or digit and holds only letters, digits, `_` and `-`. Such a name is safe as,
 * or inside, a path segment on every desktop file system.
 */
object SafeName {

    private val PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9_-]*")

    fun isValid(name: String): Boolean = PATTERN.matches(name)

    /** Throws unless [name] follows the rule; [what] names it in the message. */
    fun validate(name: String, what: String) {
        require(isValid(name)) {
            "$what '$name' must start with a letter or digit and hold only letters, digits, _ and -"
        }
    }

    /**
     * Throws unless [names] is non-empty, each follows the rule, and no two differ only in case.
     * Templates may lower- or upper-case a name, and Windows and macOS file systems ignore case, so
     * names differing only in case would collide on disk.
     */
    fun validateList(names: List<String>, what: String) {
        require(names.isNotEmpty()) { "$what must list at least one name" }
        names.forEach { validate(it, "$what entry") }
        val duplicate = names.groupBy { it.lowercase() }.values.firstOrNull { it.size > 1 }
        require(duplicate == null) { "$what entries $duplicate differ only in case" }
    }
}
