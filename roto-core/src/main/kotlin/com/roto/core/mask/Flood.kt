package com.roto.core.mask

/** Which neighbours of a pixel count as touching it. */
internal enum class Connectivity(val steps: List<Pair<Int, Int>>) {
    /** The four pixels sharing an edge. */
    FOUR(listOf(0 to -1, -1 to 0, 1 to 0, 0 to 1)),

    /** The eight pixels sharing an edge or a corner. */
    EIGHT(listOf(-1 to -1, 0 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 0 to 1, 1 to 1)),
}

/**
 * Breadth-first flood fill over a row-major `width × height` grid, the one region-growing routine
 * behind hysteresis, component labelling and hole filling. It uses an array queue and no recursion,
 * so a frame-sized region cannot overflow the stack, and it visits pixels in a fixed order.
 */
internal class Flood(private val width: Int, private val height: Int, private val connectivity: Connectivity) {

    private val queue = IntArray(width * height)

    /**
     * Gives [label] to every pixel reachable from [seeds] through [connectivity] neighbours that are
     * still unlabelled in [labels] and for which [member] holds. The seeds are labelled whatever
     * [member] says. Returns how many pixels were labelled, seeds included.
     */
    fun fill(seeds: IntArray, labels: IntArray, label: Int, member: (offset: Int) -> Boolean): Int {
        require(labels.size == width * height) { "Expected ${width * height} labels, got ${labels.size}" }
        require(label != 0) { "Label 0 marks unlabelled pixels" }
        var head = 0
        var tail = 0
        for (seed in seeds) {
            if (labels[seed] == 0) {
                labels[seed] = label
                queue[tail++] = seed
            }
        }
        while (head < tail) {
            val offset = queue[head++]
            val x = offset % width
            val y = offset / width
            for ((dx, dy) in connectivity.steps) {
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until width || ny !in 0 until height) continue
                val next = ny * width + nx
                if (labels[next] == 0 && member(next)) {
                    labels[next] = label
                    queue[tail++] = next
                }
            }
        }
        return tail
    }
}
