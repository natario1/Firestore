package com.otaliastudios.firestore

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.WriteBatch


/**
 * Initiates a batch write operation.
 */
@Suppress("unused")
public fun batchWrite(updates: FirestoreBatchWriter.() -> Unit): Task<Unit> {
    return FirestoreBatchWrite().run {
        perform(updates)
        commit()
    }
}

public class FirestoreBatchWriter internal constructor(private val batch: WriteBatch) {

    internal val ops = mutableListOf<FirestoreBatchOp>()

    /**
     * Adds a document save to the batch operation.
     */
    @Suppress("unused")
    public fun save(document: FirestoreDocument) {
        ops.add(document.save(batch))
    }


    /**
     * Adds a document deletion to the batch operation.
     */
    @Suppress("unused")
    public fun delete(document: FirestoreDocument) {
        ops.add(document.delete(batch))
    }
}

private class FirestoreBatchWrite {

    private val batch = FIRESTORE.batch()
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

internal interface FirestoreBatchOp {
    fun notifySuccess()
    fun notifyFailure()
}