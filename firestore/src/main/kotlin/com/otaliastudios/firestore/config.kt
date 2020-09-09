package com.otaliastudios.firestore

import android.util.LruCache
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.otaliastudios.firestore.parcel.DocumentReferenceParceler
import com.otaliastudios.firestore.parcel.FieldValueParceler
import com.otaliastudios.firestore.parcel.TimestampParceler
import com.otaliastudios.firestore.parcel.registerParceler

// TODO make it configurable (FirebaseApp)
internal val FIRESTORE by lazy {
    Firebase.firestore.also {
        // One time setup
        registerParceler(DocumentReferenceParceler)
        registerParceler(TimestampParceler)
        registerParceler(FieldValueParceler)
    }
}