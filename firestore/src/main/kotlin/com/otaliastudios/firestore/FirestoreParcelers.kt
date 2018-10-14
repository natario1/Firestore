/*
 * Copyright (c) 2018 Otalia Studios. Author: Mattia Iavarone.
 */

package com.otaliastudios.firestore

import android.os.Parcel
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Utility methods to parcel data.
 */
object FirestoreParcelers {

    /**
     * Parcels a possibly null DocumentReference.
     * Uses FirebaseFirestore.getInstance().
     */
    object DocumentReferenceParceler: FirestoreDocument.Parceler<DocumentReference> {

        override fun create(parcel: Parcel): DocumentReference {
            return FirebaseFirestore.getInstance().document(parcel.readString())
        }

        override fun write(data: DocumentReference, parcel: Parcel, flags: Int) {
            parcel.writeString(data.path)
        }
    }

    /**
     * Parcels a possibly null Timestamp.
     */
    object TimestampParceler: FirestoreDocument.Parceler<Timestamp> {

        override fun create(parcel: Parcel): Timestamp {
            return Timestamp(parcel.readLong(), parcel.readInt())
        }

        override fun write(data: Timestamp, parcel: Parcel, flags: Int) {
            parcel.writeLong(data.seconds)
            parcel.writeInt(data.nanoseconds)
        }
    }

    /**
     * Parcels a FieldValue
     */
    object FieldValueParceler: FirestoreDocument.Parceler<FieldValue> {

        override fun create(parcel: Parcel): FieldValue {
            val what = parcel.readString()
            when (what) {
                "delete" -> return FieldValue.delete()
                "timestamp" -> return FieldValue.serverTimestamp()
                else -> throw RuntimeException("Unknown FieldValue value: $what")
            }
        }

        override fun write(data: FieldValue, parcel: Parcel, flags: Int) {
            if (data == FieldValue.delete()) {
                parcel.writeString("delete")
            } else if (data == FieldValue.serverTimestamp()) {
                parcel.writeString("timestamp")
            } else throw RuntimeException("Cant parcel this FieldValue: $this")
        }
    }
}