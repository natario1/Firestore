/*
 * Copyright (c) 2018 Otalia Studios. Author: Mattia Iavarone.
 */

package com.otaliastudios.firestore

import androidx.annotation.Keep
import kotlin.reflect.KClass

// Keep in sync with compiler!
// And also proguard rules
@Keep
public interface FirestoreMetadata {
    public fun <T> create(key: String): T?
    public fun isNullable(key: String): Boolean
    public fun getBindableResource(key: String): Int?
    public fun <T> createInnerType(): T?

    public companion object {
        public const val SUFFIX: String = "MetadataImpl"
    }
}

private val log = FirestoreLogger("Metadata")

private val METADATA
        = mutableMapOf<String, FirestoreMetadata>()

internal val KClass<*>.metadata : FirestoreMetadata? get() {
    val name = java.name
    if (!METADATA.containsKey(name)) {
        try {
            val classPackage = java.`package`!!.name
            val className = java.simpleName
            val metadata = Class.forName("$classPackage.$className${FirestoreMetadata.SUFFIX}")
            METADATA[name] = metadata.newInstance() as FirestoreMetadata
        } catch (e: Exception) {
            log.w(e) { "Error while fetching class metadata." }
        }
    }
    return METADATA[name]
}