package com.otaliastudios.firestore

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Provides delegation (by this() or by this(name)) to [FirestoreMap] fields.
 */
internal class FirestoreFieldDelegate<MapType, ValueType : MapType>(private val field: String? = null)
    : ReadWriteProperty<FirestoreMap<MapType>, ValueType> {

    private val KProperty<*>.field get() = this@FirestoreFieldDelegate.field ?: this.name

    override fun setValue(thisRef: FirestoreMap<MapType>, property: KProperty<*>, value: ValueType) {
        thisRef[property.field] = value
    }

    override fun getValue(thisRef: FirestoreMap<MapType>, property: KProperty<*>): ValueType {
        @Suppress("UNCHECKED_CAST")
        var what = thisRef[property.field] as ValueType
        if (what == null) {
            val metadata = this::class.metadata
            if (metadata != null && !metadata.isNullable(property.field)) {
                what = metadata.create<ValueType>(property.field)!!
                thisRef[property.field] = what
                // We don't want this to be dirty now! It was just retrieved, not really set.
                // If we leave it dirty, it would not be updated on next mergeValues().
                thisRef.clearDirt(property.field)
            }
        }
        return what
    }

}