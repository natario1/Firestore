package com.otaliastudios.firestore.parcel

import android.os.Parcel
import com.google.firebase.firestore.FieldValue
import com.otaliastudios.firestore.parcel.FirestoreParceler

/**
 * Parcels a [FieldValue].
 */
public object FieldValueParceler: FirestoreParceler<FieldValue> {

    override fun create(parcel: Parcel): FieldValue {
        return when (val what = parcel.readString()) {
            "delete" -> FieldValue.delete()
            "timestamp" -> FieldValue.serverTimestamp()
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