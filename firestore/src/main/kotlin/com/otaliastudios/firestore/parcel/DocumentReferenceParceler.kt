package com.otaliastudios.firestore.parcel

import android.os.Parcel
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.otaliastudios.firestore.parcel.FirestoreParceler

/**
 * Parcels a possibly null DocumentReference.
 * Note that it uses [FirebaseFirestore.getInstance] to do so.
 */
public object DocumentReferenceParceler: FirestoreParceler<DocumentReference> {

    override fun create(parcel: Parcel): DocumentReference {
        return FirebaseFirestore.getInstance().document(parcel.readString()!!)
    }

    override fun write(data: DocumentReference, parcel: Parcel, flags: Int) {
        parcel.writeString(data.path)
    }
}