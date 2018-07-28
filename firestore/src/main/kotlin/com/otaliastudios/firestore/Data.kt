/*
 * Copyright (c) 2018 Otalia Studios. Author: Mattia Iavarone.
 */

package com.otaliastudios.firestore

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Parcel
import android.util.LruCache
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * TODO: find a better solution for parcelables.
 */
abstract class Data(
        @get:Exclude var collection: String? = null,
        @get:Exclude var id: String? = null,
        source: Map<String, Any?>? = null
) : DataMap<Any?>() {

    init {
        if (source != null) {
            mergeValues(source)
        }
    }

    @Suppress("MemberVisibilityCanBePrivate", "RedundantModalityModifier")
    @Exclude
    final fun isNew(): Boolean {
        return id == null
    }

    private fun requireReference(): DocumentReference {
        return if (id == null) {
            FIRESTORE.collection(collection!!).document()
        } else {
            FIRESTORE.collection(collection!!).document(id!!)
        }
    }

    fun getReference(): DocumentReference {
        if (id == null) throw IllegalStateException("Cant return reference for unsaved data.")
        return requireReference()
    }

    var createdAt: Timestamp? by this
    var updatedAt: Timestamp? by this

    @Exclude
    fun delete(): Task<Void> {
        return when {
            isNew() -> throw IllegalStateException("Can not delete a new object.")
            else -> getReference().delete()
        }
    }

    @Exclude
    fun <T: Data> save(): Task<T> {
        return when {
            isNew() -> create()
            else -> update()
        }
    }

    private fun <T: Data> update(): Task<T> {
        if (isNew()) throw IllegalStateException("Can not save a new object. Please call create().")
        // Collect all dirty values.
        val map = mutableMapOf<String, Any?>()
        collectDirtyValues(map, "")
        map["updatedAt"] = FieldValue.serverTimestamp()
        return getReference().update(map).onSuccessTask {
            updatedAt = Timestamp.now()
            clearDirt()
            @Suppress("UNCHECKED_CAST")
            Tasks.forResult(this as T)
        }
    }

    private fun <T: Data> create(): Task<T> {
        if (!isNew()) throw IllegalStateException("Can not create an existing object.")
        val reference = requireReference()
        // Collect all values. Can't use 'this': we can't be read by Firestore unless we have fields declared.
        // Same for DataMap and DataList. Each one need to return a Firestore-readable Map or List or whatever else.
        val map = mutableMapOf<String, Any?>()
        collectAllValues(map, "")
        map["createdAt"] = FieldValue.serverTimestamp()
        map["updatedAt"] = FieldValue.serverTimestamp()
        return reference.set(map).onSuccessTask {
            id = reference.id
            createdAt = Timestamp.now()
            updatedAt = createdAt
            clearDirt()
            @Suppress("UNCHECKED_CAST")
            Tasks.forResult(this as T)
        }
    }

    @Exclude
    fun <T: Data> trySave(vararg updates: Pair<String, Any?>): Task<T> {
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
                this@Data as T
            }
        }
    }

    companion object {

        @SuppressLint("StaticFieldLeak")
        internal val FIRESTORE = FirebaseFirestore.getInstance().apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setTimestampsInSnapshotsEnabled(true)
                .build()
        }

        internal val CACHE = LruCache<String, Data>(100)

        private val METADATA_PROVIDERS = mutableMapOf<String, DataMetadata>()
        internal val PARCELERS = mutableMapOf<String, Parceler<*>>()

        internal fun metadataProvider(klass: KClass<*>): DataMetadata {
            val name = klass.java.name
            if (!METADATA_PROVIDERS.containsKey(name)) {
                val classPackage = klass.java.`package`.name
                val className = klass.java.simpleName
                val metadata = Class.forName("$classPackage.$className${DataMetadata.SUFFIX}")
                METADATA_PROVIDERS[name] = metadata.newInstance() as DataMetadata
            }
            return METADATA_PROVIDERS[name] as DataMetadata
        }

        fun <T: Any> registerParceler(klass: KClass<T>, parceler: Parceler<*>) {
            PARCELERS[klass.java.name] = parceler
        }

        init {
            registerParceler(DocumentReference::class, Parcelers.DocumentReferenceParceler)
            registerParceler(Timestamp::class, Parcelers.TimestampParceler)
            registerParceler(FieldValue::class, Parcelers.FieldValueParceler)
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

    interface Parceler<T> {

        /**
         * Writes the [T] instance state to the [parcel].
         */
        fun write(data: T, parcel: Parcel, flags: Int)

        /**
         * Reads the [T] instance state from the [parcel], constructs the new [T] instance and returns it.
         */
        fun create(parcel: Parcel): T
    }
}


@Suppress("UNCHECKED_CAST", "RedundantVisibilityModifier")
public fun <T: Data> DocumentSnapshot.toData(type: KClass<T>): T {
    var result = Data.CACHE.get(reference.id) as? T
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
public inline fun <reified T: Data> DocumentSnapshot.toData(): T {
    return toData(T::class)
}

public fun <T: Any> dataListOf(vararg elements: T): DataList<T> {
    return DataList(elements.asList())
}

public fun <T> dataMapOf(vararg pairs: Pair<String, T>): DataMap<T> {
    return DataMap(pairs.toMap())
}