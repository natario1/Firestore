package com.otaliastudios.firestore

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.otaliastudios.firestore.batch.FirestoreBatchWrite
import com.otaliastudios.firestore.batch.FirestoreBatchWriter
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST", "RedundantVisibilityModifier")
public fun <T: FirestoreDocument> DocumentSnapshot.toFirestoreDocument(type: KClass<T>, cache: Boolean = true): T {
    var needsCacheState = false
    val result = if (cache) {
        val cached = FirestoreDocument.CACHE.get(reference.id) as? T
        if (cached == null) {
            FirestoreLogger.w("Id ${reference.id} asked for cache. Cache miss.")
            val new = type.java.newInstance()
            new.clearDirt() // Clear dirtyness from init().
            FirestoreDocument.CACHE.put(reference.id, new)
            new.cacheState = FirestoreDocument.CacheState.FRESH
            new
        } else {
            if (metadata.isFromCache) {
                FirestoreLogger.w("Id ${reference.id} asked for cache. Was found. Using CACHED_EQUAL because metadata.isFromCache.")
                cached.cacheState = FirestoreDocument.CacheState.CACHED_EQUAL
            } else {
                FirestoreLogger.w("Id ${reference.id} asked for cache. Was found. We'll see if something changed.")
                needsCacheState = true
                /* val map = mutableMapOf<String, Any?>()
                cached.collectAllValues(map, "")
                cached.cacheState = if (map.any { get(it.key) != it.value }) {
                    FirestoreLogger.v("Id ${reference.id} asked for cache. Was found. Using CACHED_CHANGED because some values were different.")
                    FirestoreDocument.CacheState.CACHED_CHANGED
                } else {
                    FirestoreLogger.v("Id ${reference.id} asked for cache. Was found. Using CACHED_EQUAL because everything matches.")
                    FirestoreDocument.CacheState.CACHED_EQUAL
                } */
            }
            cached
        }
    } else {
        FirestoreLogger.v("Id ${reference.id} created with no cache.")
        val new = type.java.newInstance()
        new.clearDirt() // Clear dirtyness from init().
        FirestoreDocument.CACHE.put(reference.id, new)
        new.cacheState = FirestoreDocument.CacheState.FRESH
        new
    }
    result.id = reference.id
    result.collection = reference.parent.path
    val changed = result.mergeValues(data!!, needsCacheState, reference.id)
    if (needsCacheState) {
        result.cacheState = if (changed) {
            FirestoreLogger.w("Id ${reference.id} NEW METHOD: It is CACHED_CHANGED.")
            FirestoreDocument.CacheState.CACHED_CHANGED
        } else {
            FirestoreLogger.w("Id ${reference.id} NEW METHOD: It is CACHED_EQUAL.")
            FirestoreDocument.CacheState.CACHED_EQUAL
        }
    }
    return result
}

@Suppress("RedundantVisibilityModifier")
public inline fun <reified T: FirestoreDocument> DocumentSnapshot.toFirestoreDocument(cache: Boolean = true): T {
    return toFirestoreDocument(T::class, cache)
}

public fun <T: Any> firestoreListOf(vararg elements: T): FirestoreList<T> {
    return FirestoreList(elements.asList())
}

public fun <T> firestoreMapOf(vararg pairs: Pair<String, T>): FirestoreMap<T> {
    return FirestoreMap(pairs.toMap())
}

public fun batchWrite(updates: FirestoreBatchWriter.() -> Unit): Task<Unit> {
    return FirestoreBatchWrite().run {
        perform(updates)
        commit()
    }
}