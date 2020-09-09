package com.otaliastudios.firestore.parcel

import android.os.Parcel
import com.otaliastudios.firestore.FirestoreLogger
import kotlin.reflect.KClass

private val PARCELERS = mutableMapOf<String, FirestoreParceler<*>>()

/**
 * Registers a new [FirestoreParceler].
 */
public fun <T: Any> registerParceler(klass: KClass<T>, parceler: FirestoreParceler<T>) {
    PARCELERS[klass.java.name] = parceler
}

/**
 * Registers a new [FirestoreParceler].
 */
@Suppress("unused")
public inline fun <reified T: Any> registerParceler(parceler: FirestoreParceler<T>) {
    registerParceler(T::class, parceler)
}

private val log = FirestoreLogger("Parcelers")
private const val NULL = 0
private const val PARCELER = 1
private const val VALUE = 2

internal fun Parcel.writeValue(value: Any?, tag: String) {
    if (value == null) {
        log.v { "$tag writeParcel: value is null." }
        writeInt(NULL)
        return
    }
    val klass = value::class.java.name
    if (PARCELERS.containsKey(klass)) {
        log.v { "$tag writeParcel: value $value will be written with parceler for class $klass." }
        writeInt(PARCELER)
        writeString(klass)
        @Suppress("UNCHECKED_CAST")
        val parceler = PARCELERS[klass] as FirestoreParceler<Any>
        parceler.write(value, this, 0)
        return
    }
    try {
        log.v { "$tag writeParcel: value $value will be written with writeValue()." }
        writeInt(VALUE)
        writeValue(value)
    } catch (e: Exception) {
        log.e(e) { "Could not write value $value. You need to add a FirestoreParceler." }
        throw e
    }
}

internal fun Parcel.readValue(loader: ClassLoader, tag: String): Any? {
    val what = readInt()
    if (what == NULL) {
        log.v { "$tag readParcel: value is null." }
        return null
    }
    if (what == PARCELER) {
        val klass = readString()!!
        log.v { "$tag readParcel: value will be read by parceler $klass." }
        @Suppress("UNCHECKED_CAST")
        val parceler = PARCELERS[klass] as FirestoreParceler<Any>
        return parceler.create(this)
    }
    if (what == VALUE) {
        val read = readValue(loader)
        log.v { "$tag readParcel: value was read by readValue: $read." }
        return read
    }
    val e = IllegalStateException("$tag Error while reading parcel. Unexpected control int: $what")
    log.e(e) { e.message!! }
    throw e
}
