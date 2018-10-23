package com.otaliastudios.firestore

import android.os.Parcel
import com.google.firebase.Timestamp

/**
 * Parcels a possibly null Timestamp.
 */
object TimestampParceler: FirestoreParceler<Timestamp> {

    override fun create(parcel: Parcel): Timestamp {
        return Timestamp(parcel.readLong(), parcel.readInt())
    }

    override fun write(data: Timestamp, parcel: Parcel, flags: Int) {
        parcel.writeLong(data.seconds)
        parcel.writeInt(data.nanoseconds)
    }
}