package com.otaliastudios.firestore

import com.google.firebase.firestore.DocumentSnapshot
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST", "RedundantVisibilityModifier")
public fun <T: FirestoreDocument> DocumentSnapshot.toFirestoreDocument(type: KClass<T>, cache: Boolean = true): T {
    val result = if (cache) {
        val cached = FirestoreDocument.CACHE.get(reference.id) as? T
        if (cached == null) {
            FirestoreLogger.v("Id ${reference.id} asked for cache. Cache miss.")
            val new = type.java.newInstance()
            new.clearDirt() // Clear dirtyness from init().
            FirestoreDocument.CACHE.put(reference.id, new)
            new
        } else {
            FirestoreLogger.v("Id ${reference.id} asked for cache. Was found.")
            cached
        }
    } else {
        FirestoreLogger.v("Id ${reference.id} created with no cache.")
        val new = type.java.newInstance()
        new.clearDirt() // Clear dirtyness from init().
        FirestoreDocument.CACHE.put(reference.id, new)
        new
    }
    result.id = reference.id
    result.collection = reference.parent.path
    result.mergeValues(data!!)
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