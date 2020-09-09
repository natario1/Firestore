package com.otaliastudios.firestore.parcel

import android.os.Parcel
import com.google.firebase.Timestamp
import com.otaliastudios.firestore.parcel.FirestoreParceler

/**
 * Parcels a possibly null Timestamp.
 */
public object TimestampParceler: FirestoreParceler<Timestamp> {

    override fun create(parcel: Parcel): Timestamp {
        return Timestamp(parcel.readLong(), parcel.readInt())
    }

    override fun write(data: Timestamp, parcel: Parcel, flags: Int) {
        parcel.writeLong(data.seconds)
        parcel.writeInt(data.nanoseconds)
    }
}