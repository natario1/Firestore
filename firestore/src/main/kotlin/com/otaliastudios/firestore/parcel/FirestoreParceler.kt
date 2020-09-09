package com.otaliastudios.firestore.parcel

import android.os.Parcel
import kotlin.reflect.KClass

public interface FirestoreParceler<T: Any> {

    /**
     * Writes the [T] instance state to the [parcel].
     */
    public fun write(data: T, parcel: Parcel, flags: Int)

    /**
     * Reads the [T] instance state from the [parcel], constructs the new [T] instance and returns it.
     */
    public fun create(parcel: Parcel): T

    public companion object {
        /**
         * Registers a new [FirestoreParceler].
         */
        @Suppress("unused")
        public fun <T: Any> register(klass: KClass<T>, parceler: FirestoreParceler<T>) {
            registerParceler(klass, parceler)
        }

        /**
         * Registers a new [FirestoreParceler].
         */
        @Suppress("unused")
        public inline fun <reified T: Any> register(parceler: FirestoreParceler<T>) {
            registerParceler(T::class, parceler)
        }
    }
}