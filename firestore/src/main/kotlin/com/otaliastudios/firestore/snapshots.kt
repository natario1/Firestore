package com.otaliastudios.firestore

import com.google.firebase.firestore.DocumentSnapshot
import kotlin.reflect.KClass

private val log = FirestoreLogger("Snapshots")

/**
 * Converts the given [DocumentSnapshot] to a [FirestoreDocument].
 * The [cache] boolean tells whether we should inspect the cache before allocating a new object.
 */
@Suppress("unused")
public inline fun <reified T: FirestoreDocument> DocumentSnapshot.toFirestoreDocument(cache: Boolean = true): T {
    return toFirestoreDocument(T::class, cache)
}

/**
 * Converts the given [DocumentSnapshot] to a [FirestoreDocument].
 * The [cache] boolean tells whether we should inspect the cache before allocating a new object.
 */
@Suppress("UNCHECKED_CAST")
public fun <T: FirestoreDocument> DocumentSnapshot.toFirestoreDocument(type: KClass<T>, cache: Boolean = true): T {
    var needsCacheState = false
    val result = if (cache) {
        val cached = FirestoreCache.get(reference.id, type)
        if (cached == null) {
            log.i { "Id ${reference.id} asked for cache. Cache miss." }
            val new = type.java.newInstance()
            new.clearDirt() // Clear dirtyness from init().
            FirestoreCache[reference.id] = new
            new.cacheState = FirestoreCacheState.FRESH
            new
        } else {
            if (metadata.isFromCache) {
                log.i { "Id ${reference.id} asked for cache. Was found. Using CACHED_EQUAL because metadata.isFromCache." }
                cached.cacheState = FirestoreCacheState.CACHED_EQUAL
            } else {
                log.i { "Id ${reference.id} asked for cache. Was found. We'll see if something changed." }
                needsCacheState = true
            }
            cached
        }
    } else {
        log.i { "Id ${reference.id} created with no cache." }
        val new = type.java.newInstance()
        new.clearDirt() // Clear dirtyness from init().
        FirestoreCache[reference.id] = new
        new.cacheState = FirestoreCacheState.FRESH
        new
    }
    result.id = reference.id
    result.collection = reference.parent.path
    val changed = result.mergeValues(data!!, needsCacheState, reference.id)
    if (needsCacheState) {
        result.cacheState = if (changed) {
            log.v { "Id ${reference.id} Setting cache state to CACHED_CHANGED." }
            FirestoreCacheState.CACHED_CHANGED
        } else {
            log.v { "Id ${reference.id} Setting cache state to CACHED_EQUAL." }
            FirestoreCacheState.CACHED_EQUAL
        }
    }
    return result
}
