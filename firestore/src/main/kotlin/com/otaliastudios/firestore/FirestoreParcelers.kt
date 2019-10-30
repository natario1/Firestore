/*
 * Copyright (c) 2018 Otalia Studios. Author: Mattia Iavarone.
 */

package com.otaliastudios.firestore

import android.os.Parcel
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.reflect.KClass

/**
 * Parceling engine.
 */
object FirestoreParcelers {

    internal val MAP = mutableMapOf<String, FirestoreParceler<*>>()

    fun <T: Any> add(klass: KClass<T>, parceler: FirestoreParceler<T>) {
        MAP[klass.java.name] = parceler
    }

    private const val NULL = 0
    private const val PARCELER = 1
    private const val VALUE = 2

    internal fun write(parcel: Parcel, value: Any?, tag: String) {
        if (value == null) {
            FirestoreLogger.v { "$tag writeParcel: value is null." }
            parcel.writeInt(NULL)
            return
        }
        val klass = value::class.java.name
        if (MAP.containsKey(klass)) {
            FirestoreLogger.v { "$tag writeParcel: value $value will be written with parceler for class $klass." }
            parcel.writeInt(PARCELER)
            parcel.writeString(klass)
            @Suppress("UNCHECKED_CAST")
            val parceler = MAP[klass] as FirestoreParceler<Any>
            parceler.write(value, parcel, 0)
            return
        }
        try {
            FirestoreLogger.v { "$tag writeParcel: value $value will be written with writeValue()." }
            parcel.writeInt(VALUE)
            parcel.writeValue(value)
        } catch (e: Exception) {
            FirestoreLogger.e(e) { "Could not write value $value. You need to add a FirestoreParceler." }
            throw e
        }
    }

    internal fun read(parcel: Parcel, loader: ClassLoader, tag: String): Any? {
        val what = parcel.readInt()
        if (what == NULL) {
            FirestoreLogger.v { "$tag readParcel: value is null." }
            return null
        }
        if (what == PARCELER) {
            val klass = parcel.readString()!!
            FirestoreLogger.v { "$tag readParcel: value will be read by parceler $klass." }
            @Suppress("UNCHECKED_CAST")
            val parceler = MAP[klass] as FirestoreParceler<Any>
            return parceler.create(parcel)
        }
        if (what == VALUE) {
            val read = parcel.readValue(loader)
            FirestoreLogger.v { "$tag readParcel: value was read by readValue: $read." }
            return read
        }
        val e = IllegalStateException("$tag Error while reading parcel. Unexpected control int: $what")
        FirestoreLogger.e(e) { e.message!! }
        throw e
    }


}