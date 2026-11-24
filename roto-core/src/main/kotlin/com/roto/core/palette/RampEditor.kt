package com.roto.core.palette

import com.roto.core.color.OkLab

/**
 * Builds a [Ramp] from the colours the artist picked: sorts them dark to light by OKLab lightness,
 * so the order they were clicked in does not matter, and says in plain words why a pick cannot be a
 * ramp. Every failure is an [IllegalArgumentException] whose message is fit to show the artist.
 */
object RampEditor {
    /**
     * The ramp called [name] made of [picked], darkest first.
     *
     * @throws IllegalArgumentException when [name] is blank, nothing is picked, a colour is not in
     * [palette], or two colours have the same lightness (the message names both)
     */
    fun build(name: String, picked: List<Int>, palette: Palette): Ramp {
        require(name.isNotBlank()) { "A ramp needs a name" }
        require(picked.isNotEmpty()) { "Pick at least one colour for ramp '$name'" }
        picked.firstOrNull { it !in palette }?.let { colour ->
            throw IllegalArgumentException("${HexColor.format(colour)} is not in the palette")
        }
        val sorted = picked.sortedBy { OkLab.fromArgb(it).l }
        sorted.zipWithNext().firstOrNull { (darker, lighter) -> lightness(darker) == lightness(lighter) }?.let {
            throw IllegalArgumentException(
                "${HexColor.format(it.first)} and ${HexColor.format(it.second)} are equally light, " +
                    "so a ramp cannot tell them apart",
            )
        }
        return Ramp(name, sorted)
    }

    private fun lightness(argb: Int): Double = OkLab.fromArgb(argb).l
}
