package com.otaliastudios.firestore

import android.os.Parcel

interface FirestoreParceler<T: Any> {

    /**
     * Writes the [T] instance state to the [parcel].
     */
    fun write(data: T, parcel: Parcel, flags: Int)

    /**
     * Reads the [T] instance state from the [parcel], constructs the new [T] instance and returns it.
     */
    fun create(parcel: Parcel): T
}