/*
 * Copyright (c) 2018 Otalia Studios. Author: Mattia Iavarone.
 */

package com.otaliastudios.firestore

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.LruCache
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import kotlin.reflect.KClass

/**
 * The base document class.
 */
abstract class FirestoreDocument(
        @get:Exclude var collection: String? = null,
        @get:Exclude var id: String? = null,
        source: Map<String, Any?>? = null
) : FirestoreMap<Any?>(source = source) {

    @Suppress("MemberVisibilityCanBePrivate", "RedundantModalityModifier")
    @Exclude
    final fun isNew(): Boolean {
        return createdAt == null
    }

    private fun requireReference(): DocumentReference {
        return if (id == null) {
            FIRESTORE.collection(collection!!).document()
        } else {
            FIRESTORE.collection(collection!!).document(id!!)
        }
    }

    @Exclude
    fun getReference(): DocumentReference {
        if (id == null) throw IllegalStateException("Cant return reference for unsaved data.")
        return requireReference()
    }

    var createdAt: Timestamp? by this
    var updatedAt: Timestamp? by this

    internal var cacheState: CacheState = CacheState.FRESH

    @Exclude
    fun getCacheState() = cacheState

    @Exclude
    fun delete(): Task<Unit> {
        @Suppress("UNCHECKED_CAST")
        return getReference().delete() as Task<Unit>
    }

    @Exclude
    fun <T: FirestoreDocument> save(): Task<T> {
        return when {
            isNew() -> create()
            else -> update()
        }
    }

    private fun <T: FirestoreDocument> update(): Task<T> {
        if (isNew()) throw IllegalStateException("Can not save a new object. Please call create().")
        // Collect all dirty values.
        val map = mutableMapOf<String, Any?>()
        flattenValues(map, "", dirtyOnly = true)
        map["updatedAt"] = FieldValue.serverTimestamp()
        return getReference().update(map).onSuccessTask {
            updatedAt = Timestamp.now()
            clearDirt()
            @Suppress("UNCHECKED_CAST")
            Tasks.forResult(this as T)
        }
    }

    private fun <T: FirestoreDocument> create(): Task<T> {
        if (!isNew()) throw IllegalStateException("Can not create an existing object.")
        val reference = requireReference()
        // Collect all values. Can't use 'this': we can't be read by Firestore unless we have fields declared.
        // Same for FirestoreMap and FirestoreList. Each one need to return a Firestore-readable Map or List or whatever else.
        // Can't use the [flatten] API or this won't work with . fields.
        val map = collectValues(dirtyOnly = false).toMutableMap()
        map["createdAt"] = FieldValue.serverTimestamp()
        map["updatedAt"] = FieldValue.serverTimestamp()
        // Add to cache NOW, then eventually revert.
        // This is because when reference.set() succeeds, any query listener is notified
        // before our onSuccessTask() is called. So a new item is created.
        FirestoreDocument.CACHE.put(reference.id, this)
        return reference.set(map).addOnFailureListener {
            FirestoreDocument.CACHE.remove(reference.id)
        }.onSuccessTask {
            id = reference.id
            createdAt = Timestamp.now()
            updatedAt = createdAt
            clearDirt()
            @Suppress("UNCHECKED_CAST")
            Tasks.forResult(this as T)
        }
    }

    @Exclude
    fun <T: FirestoreDocument> trySave(vararg updates: Pair<String, Any?>): Task<T> {
        if (isNew()) throw IllegalStateException("Can not trySave a new object. Please call save() first.")
        val reference = requireReference()
        val values = updates.toMap().toMutableMap()
        values["updatedAt"] = FieldValue.serverTimestamp()
        if (isNew()) values["createdAt"] = FieldValue.serverTimestamp()
        return reference.update(values).continueWith {
            if (it.exception != null) {
                throw it.exception!!
            } else {
                id = reference.id
                values.forEach { (key, value) ->
                    set(key, value)
                    clearDirt(key)
                }
                updatedAt = Timestamp.now()
                createdAt = createdAt ?: updatedAt
                clearDirt("updatedAt")
                clearDirt("createdAt")
                @Suppress("UNCHECKED_CAST")
                this@FirestoreDocument as T
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is FirestoreDocument &&
                other.id == this.id &&
                other.collection == this.collection &&
                super.equals(other)
    }

    companion object {

        @SuppressLint("StaticFieldLeak")
        internal val FIRESTORE = FirebaseFirestore.getInstance().apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setTimestampsInSnapshotsEnabled(true)
                .build()
        }

        internal val CACHE = LruCache<String, FirestoreDocument>(100)

        private val METADATA_PROVIDERS = mutableMapOf<String, FirestoreMetadata>()

        internal fun metadataProvider(klass: KClass<*>): FirestoreMetadata {
            val name = klass.java.name
            if (!METADATA_PROVIDERS.containsKey(name)) {
                val classPackage = klass.java.`package`.name
                val className = klass.java.simpleName
                val metadata = Class.forName("$classPackage.$className${FirestoreMetadata.SUFFIX}")
                METADATA_PROVIDERS[name] = metadata.newInstance() as FirestoreMetadata
            }
            return METADATA_PROVIDERS[name] as FirestoreMetadata
        }

        init {
            FirestoreParcelers.add(DocumentReference::class, DocumentReferenceParceler)
            FirestoreParcelers.add(Timestamp::class, TimestampParceler)
            FirestoreParcelers.add(FieldValue::class, FieldValueParceler)
        }

        fun <T: FirestoreDocument> getCached(id: String, type: KClass<T>): T? {
            @Suppress("UNCHECKED_CAST")
            return CACHE.get(id) as? T
        }

        inline fun <reified T: FirestoreDocument> getCached(id: String): T? {
            return getCached(id, T::class)
        }
    }

    override fun onWriteToBundle(bundle: Bundle) {
        super.onWriteToBundle(bundle)
        bundle.putString("id", id)
        bundle.putString("collection", collection)
    }

    override fun onReadFromBundle(bundle: Bundle) {
        super.onReadFromBundle(bundle)
        id = bundle.getString("id", null)
        collection = bundle.getString("collection", null)
    }

    enum class CacheState {
        FRESH, CACHED_EQUAL, CACHED_CHANGED
    }
}
