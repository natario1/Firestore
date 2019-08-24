package com.otaliastudios.firestore.batch

import com.google.firebase.firestore.WriteBatch
import com.otaliastudios.firestore.FirestoreDocument

class FirestoreBatchWriter internal constructor(private val batch: WriteBatch) {

    internal val ops = mutableListOf<FirestoreDocument.BatchOp>()

    fun save(document: FirestoreDocument) {
        ops.add(document.save(batch))
    }

    public fun delete(document: FirestoreDocument) {
        ops.add(document.delete(batch))
    }
}