package ru.kyamshanov.notepen

/**
 * Simple LRU cache backed by [LinkedHashMap].
 *
 * Evicts the least-recently-accessed entry when [maxSize] is exceeded.
 * Not thread-safe — call sites must synchronise if needed.
 */
class LruCache<K, V>(private val maxSize: Int) {

    private val map = object : LinkedHashMap<K, V>(maxSize + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
            size > maxSize
    }

    operator fun get(key: K): V? = map[key]
    operator fun set(key: K, value: V) { map[key] = value }
    fun remove(key: K): V? = map.remove(key)
    fun clear() = map.clear()
}
