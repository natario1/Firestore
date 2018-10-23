package com.otaliastudios.firestore

import android.os.Parcel
import com.google.firebase.firestore.FieldValue

/**
 * Parcels a FieldValue
 */
object FieldValueParceler: FirestoreParceler<FieldValue> {

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