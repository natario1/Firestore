package com.otaliastudios.firestore

import android.util.LruCache
import kotlin.reflect.KClass

public object FirestoreCache {
    // TODO make size configurable
    private val cache = LruCache<String, FirestoreDocument>(100)

    @Suppress("UNCHECKED_CAST")
    public fun <T: FirestoreDocument> get(id: String, type: KClass<T>): T? {
        val cached = cache[id] ?: return null
        require(type.isInstance(cached)) { "Cached object is not of the given type: $type / ${cached::class}"}
        return cached as T
    }

    @Suppress("unused")
    public inline operator fun <reified T: FirestoreDocument> get(id: String): T? {
        return get(id, T::class)
    }

    internal operator fun <T: FirestoreDocument> set(id: String, value: T) {
        cache.put(id, value)
    }

    internal fun remove(id: String) {
        cache.remove(id)
    }
}

public enum class FirestoreCacheState {
    FRESH, CACHED_EQUAL, CACHED_CHANGED
}