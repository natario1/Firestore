/*
 * Copyright (c) 2018 Otalia Studios. Author: Mattia Iavarone.
 */

package com.otaliastudios.firestore

import android.os.Bundle
import androidx.annotation.Keep
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import com.otaliastudios.firestore.parcel.DocumentReferenceParceler
import com.otaliastudios.firestore.parcel.FieldValueParceler
import com.otaliastudios.firestore.parcel.TimestampParceler
import kotlin.reflect.KClass

/**
 * The base document class.
 */
@Keep
public abstract class FirestoreDocument(
        @get:Exclude public var collection: String? = null,
        @get:Exclude public var id: String? = null,
        source: Map<String, Any?>? = null
) : FirestoreMap<Any?>(source = source) {

    @Suppress("MemberVisibilityCanBePrivate", "RedundantModalityModifier")
    @Exclude
    public final fun isNew(): Boolean {
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
    public fun getReference(): DocumentReference {
        if (id == null) throw IllegalStateException("Cant return reference for unsaved data.")
        return requireReference()
    }

    @get:Keep @set:Keep
    public var createdAt: Timestamp? by this

    @get:Keep @set:Keep
    public var updatedAt: Timestamp? by this

    internal var cacheState: FirestoreCacheState = FirestoreCacheState.FRESH

    @Suppress("unused")
    @Exclude
    public fun getCacheState(): FirestoreCacheState = cacheState

    @Suppress("unused")
    @Exclude
    public fun delete(): Task<Unit> {
        @Suppress("UNCHECKED_CAST")
        return getReference().delete() as Task<Unit>
    }

    @Exclude
    internal fun delete(batch: WriteBatch): FirestoreBatchOp {
        batch.delete(getReference())
        return object: FirestoreBatchOp {
            override fun notifyFailure() {}
            override fun notifySuccess() {}
        }
    }

    @Suppress("unused")
    @Exclude
    public fun <T: FirestoreDocument> save(): Task<T> {
        return when {
            isNew() -> create()
            else -> update()
        }
    }

    @Exclude
    internal fun save(batch: WriteBatch): FirestoreBatchOp {
        return when {
            isNew() -> create(batch)
            else -> update(batch)
        }
    }

    private fun <T: FirestoreDocument> update(): Task<T> {
        if (isNew()) throw IllegalStateException("Can not update a new object. Please call create().")
        val map = mutableMapOf<String, Any?>()
        flattenValues(map, prefix = "", dirtyOnly = true)
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
        FirestoreCache[reference.id] = this
        return reference.set(map).addOnFailureListener {
            FirestoreCache.remove(reference.id)
        }.onSuccessTask {
            id = reference.id
            createdAt = Timestamp.now()
            updatedAt = createdAt
            clearDirt()
            @Suppress("UNCHECKED_CAST")
            Tasks.forResult(this as T)
        }
    }

    private fun update(batch: WriteBatch): FirestoreBatchOp {
        if (isNew()) throw IllegalStateException("Can not update a new object. Please call create().")
        val map = mutableMapOf<String, Any?>()
        flattenValues(map, prefix = "", dirtyOnly = true)
        map["updatedAt"] = FieldValue.serverTimestamp()
        batch.update(getReference(), map)
        return object: FirestoreBatchOp {
            override fun notifyFailure() {}
            override fun notifySuccess() {
                updatedAt = Timestamp.now()
                clearDirt()
            }
        }
    }

    private fun create(batch: WriteBatch): FirestoreBatchOp {
        if (!isNew()) throw IllegalStateException("Can not create an existing object.")
        val reference = requireReference()
        val map = collectValues(dirtyOnly = false).toMutableMap()
        map["createdAt"] = FieldValue.serverTimestamp()
        map["updatedAt"] = FieldValue.serverTimestamp()
        batch.set(reference, map)
        FirestoreCache[reference.id] = this
        return object: FirestoreBatchOp {
            override fun notifyFailure() {
                FirestoreCache.remove(reference.id)
            }

            override fun notifySuccess() {
                id = reference.id
                createdAt = Timestamp.now()
                updatedAt = createdAt
                clearDirt()
            }
        }
    }

    @Suppress("unused")
    @Exclude
    public fun <T: FirestoreDocument> trySave(vararg updates: Pair<String, Any?>): Task<T> {
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is FirestoreDocument &&
                other.id == this.id &&
                other.collection == this.collection &&
                super.equals(other)
    }

    public companion object {
        @Deprecated(message = "Use FirestoreCache directly.", replaceWith = ReplaceWith("FirestoreCache.get"))
        public fun <T: FirestoreDocument> getCached(id: String, type: KClass<T>): T?
                = FirestoreCache.get(id, type)

        @Suppress("unused")
        @Deprecated(message = "Use FirestoreCache directly.", replaceWith = ReplaceWith("FirestoreCache.get"))
        public inline fun <reified T: FirestoreDocument> getCached(id: String): T?
                = FirestoreCache[id]
    }
}
