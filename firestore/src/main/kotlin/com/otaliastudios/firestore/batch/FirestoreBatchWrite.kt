package com.otaliastudios.firestore.batch

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.otaliastudios.firestore.FirestoreDocument

class FirestoreBatchWrite internal constructor() {

    private val batch = FirestoreDocument.FIRESTORE.batch()
    private val writer = FirestoreBatchWriter(batch)

    fun perform(action: FirestoreBatchWriter.() -> Unit): FirestoreBatchWrite {
        action(writer)
        return this
    }

    fun commit(): Task<Unit> {
        return batch.commit().addOnSuccessListener {
            writer.ops.forEach { it.notifySuccess() }
        }.addOnFailureListener {
            writer.ops.forEach { it.notifyFailure() }
        }.onSuccessTask {
            Tasks.forResult(Unit)
        }
    }
}