package com.otaliastudios.firestore

import com.google.firebase.firestore.DocumentSnapshot
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST", "RedundantVisibilityModifier")
public fun <T: FirestoreDocument> DocumentSnapshot.toFirestoreDocument(type: KClass<T>): T {
    var result = FirestoreDocument.CACHE.get(reference.id) as? T
    if (result == null) {
        result = type.java.newInstance()!!
        result.clearDirt() // Clear dirtyness from init().
    }
    result.id = reference.id
    result.collection = reference.parent.path
    result.mergeValues(data!!)
    return result
}

@Suppress("RedundantVisibilityModifier")
public inline fun <reified T: FirestoreDocument> DocumentSnapshot.toFirestoreDocument(): T {
    return toFirestoreDocument(T::class)
}

public fun <T: Any> firestoreListOf(vararg elements: T): FirestoreList<T> {
    return FirestoreList(elements.asList())
}

public fun <T> firestoreMapOf(vararg pairs: Pair<String, T>): FirestoreMap<T> {
    return FirestoreMap(pairs.toMap())
}