package com.roto.core.cache

/**
 * One stage's output as the next stage takes it: its [key], known up front, and its value, produced
 * on the first [get] and kept after that.
 *
 * A stage keys its own output from the keys of its inputs and reads their values only when its own
 * output is not cached. So when the last stage of a chain is cached, nothing before it is extracted,
 * decoded or computed at all.
 */
class StageOutput<T : Any>(val key: CacheKey, produce: () -> T) {

    private val value by lazy(produce)

    fun get(): T = value

    override fun toString(): String = "StageOutput($key)"
}
