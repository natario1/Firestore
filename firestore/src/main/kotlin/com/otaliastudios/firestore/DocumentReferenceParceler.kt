package com.otaliastudios.firestore

import android.os.Parcel
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Parcels a possibly null DocumentReference.
 * Uses FirebaseFirestore.getInstance().
 */
object DocumentReferenceParceler: FirestoreParceler<DocumentReference> {

    override fun create(parcel: Parcel): DocumentReference {
        return FirebaseFirestore.getInstance().document(parcel.readString()!!)
    }

    override fun write(data: DocumentReference, parcel: Parcel, flags: Int) {
        parcel.writeString(data.path)
    }
}