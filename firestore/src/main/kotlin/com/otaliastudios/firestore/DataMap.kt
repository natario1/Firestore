/*
 * Copyright (c) 2018 Otalia Studios. Author: Mattia Iavarone.
 */

package com.otaliastudios.firestore

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import androidx.databinding.BaseObservable
import com.google.firebase.firestore.Exclude
import kotlin.reflect.KProperty

/**
 * A map implementation. Delegates to a mutable map.
 * Introduce dirtyness checking for childrens.
 */
open class DataMap<T>(
        source: Map<String, T>? = null
) : BaseObservable(), /*MutableMap<String, T> by data,*/ Parcelable {

    private val data: MutableMap<String, T> = mutableMapOf()
    private val dirty: MutableSet<String> = mutableSetOf()

    @get:Exclude
    val keys = data.keys

    @get:Exclude
    val size = data.size

    init {
        if (source != null) {
            mergeValues(source)
        }
    }

    internal fun isDirty(): Boolean {
        return keys.any { isDirty(it) }
    }

    internal fun isDirty(key: String): Boolean {
        if (dirty.contains(key)) return true
        if (key.contains('.')) {
            val first = key.split('.')[0]
            val second = key.removePrefix("$first.")
            val data = get(first)
            return when (data) {
                null -> false
                is DataMap<*> -> data.isDirty(second)
                else -> throw IllegalArgumentException("Accessing with dot notation, but it is not a DataMap.")
            }
        }
        val what = get(key)
        if (what is DataList<*>) {
            return what.isDirty()
        } else {
            return false
        }
    }

    internal fun clearDirt() {
        for (key in keys) {
            clearDirt(key)
        }
    }

    internal fun clearDirt(key: String) {
        if (dirty.contains(key)) {
            dirty.remove(key)
        } else {
            val value = get(key)
            if (value is DataMap<*>) {
                value.clearDirt()
            } else if (value is DataList<*>) {
                value.clearDirt()
            }
        }
    }


    private fun <K> createDataMap(key: String): DataMap<K> {
        val map = try { onCreateDataMap<K>(key) } catch (e: Exception) {
            DataMap<K>()
        }
        map.clearDirt()
        return map
    }

    private fun <K: Any> createDataList(key: String): DataList<K> {
        val list = try { onCreateDataList<K>(key) } catch (e: Exception) {
            DataList<K>()
        }
        list.clearDirt()
        return list
    }

    protected open fun <K> onCreateDataMap(key: String): DataMap<K> {
        val provider = DataDocument.metadataProvider(this::class)
        var candidate = provider.create<DataMap<K>>(key)
        candidate = candidate ?: provider.createInnerType()
        candidate = candidate ?: DataMap()
        return candidate
    }

    protected open fun <K: Any> onCreateDataList(key: String): DataList<K> {
        val provider = DataDocument.metadataProvider(this::class)
        var candidate = provider.create<DataList<K>>(key)
        candidate = candidate ?: provider.createInnerType()
        candidate = candidate ?: DataList()
        return candidate
    }

    final operator fun set(key: String, value: T) {
        val result = onSet(key, value)
        /* if (result == null) {
            // Do nothing.
        } else */if (key.contains('.')) {
            val first = key.split('.')[0]
            val second = key.removePrefix("$first.")
            val data = getOrCreateDataMap(first)
            data[second] = result
        } else {
            data[key] = result
            dirty.add(key)
            val resource = DataDocument.metadataProvider(this::class).getBindableResource(key)
            if (resource != null) notifyPropertyChanged(resource)
        }
    }

    internal open fun onSet(key: String, value: T): T = value

    final operator fun get(key: String): T? {
        return if (key.contains('.')) {
            val first = key.split('.')[0]
            val second = key.removePrefix("$first.")
            val data = getOrCreateDataMap(first)
            data[second]
        } else {
            data[key]
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getOrCreateDataMap(key: String): DataMap<T> {
        val data = get(key)
        if (data == null) {
            val map = createDataMap<T>(key)
            set(key, map as T)
            return map
        } else if (data is DataMap<*>) {
            return data as DataMap<T>
        } else {
            throw RuntimeException("Trying to access map with dot notation, " +
                    "but it is not a DataMap. key: $key, value: $data")
        }
    }

    protected operator fun <R: T> getValue(source: DataMap<T>, property: KProperty<*>): R {
        @Suppress("UNCHECKED_CAST")
        var what = source[property.name] as R
        if (what == null) {
            val provider = DataDocument.metadataProvider(this::class)
            if (!provider.isNullable(property.name)) {
                what = provider.create<R>(property.name)!!
            }
        }
        /* if (what == null && !property.returnType.isMarkedNullable) {
            what = property.returnType.createInstance()
        } */
        return what
    }

    protected operator fun <R: T> setValue(source: DataMap<T>, property: KProperty<*>, what: R) {
        source[property.name] = what
    }

    internal fun collectDirtyValues(map: MutableMap<String, Any?>, prefix: String) {
        for (key in keys) {
            val child = get(key)
            val childPrefix = "$prefix.$key".trim('.')
            if (dirty.contains(key)) {
                map[childPrefix] = child
            } else if (child is DataMap<*>) {
                child.collectDirtyValues(map, childPrefix)
            } else if (child is DataList<*>) {
                child.collectDirtyValues(map, childPrefix)
            }
        }
    }

    internal fun collectAllValues(map: MutableMap<String, Any?>, prefix: String) {
        for (key in keys) {
            val child = get(key)
            val childPrefix = "$prefix.$key".trim('.')
            if (child is DataMap<*>) {
                child.collectAllValues(map, childPrefix)
            } else if (child is DataList<*>) {
                child.collectAllValues(map, childPrefix)
            } else {
                map[childPrefix] = child
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal fun mergeValues(values: Map<String, T>) {
        for ((key, value) in values) {
            if (isDirty(key)) continue
            if (value is Map<*, *> && value.keys.all { it is String }) {
                val child = get(key) ?: createDataMap<Any?>(key) as T // T
                data[key] = child
                child as DataMap<Any?>
                value as Map<String, Any?>
                child.mergeValues(value)
            } else if (value is List<*>) {
                val child = get(key) ?: createDataList<Any>(key) as T // T
                data[key] = child
                child as DataList<Any>
                value as List<Any>
                child.mergeValues(value)
            } else {
                data[key] = value
            }
        }
    }

    fun toSafeMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        collectAllValues(map, "")
        return map
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is DataMap<*> &&
                other.data.size == data.size &&
                other.data.all { it.value == data[it.key] } &&
                other.dirty.size == dirty.size &&
                other.dirty.containsAll(dirty)
        // TODO it's better to collect the dirty keys, though it makes everything slow.
    }

    override fun hashCode(): Int {
        var result = data.hashCode()
        result = 31 * result + dirty.hashCode()
        return result
    }

    override fun describeContents() = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(this::class.java.name)
        parcel.writeInt(dirty.size)
        parcel.writeStringArray(dirty.toTypedArray())

        parcel.writeInt(size)
        val checks = Parcel.obtain()
        for ((key, value) in data) {
            parcel.writeString(key)
            val canWrite = try {
                checks.writeValue(value)
                true
            } catch (e: Exception) { false }
            if (canWrite) {
                parcel.writeString("value")
                parcel.writeValue(value)
            } else {
                val className = (value as Any)::class.java.name
                parcel.writeString(className)
                @Suppress("UNCHECKED_CAST")
                val parceler = DataDocument.PARCELERS[className] as? DataDocument.Parceler<Any?>
                if (parceler == null) throw IllegalStateException("Can not parcel type $className. Please register a parceler using DataDocument.registerParceler.")
                parceler.write(value, parcel, 0)
            }
        }

        val bundle = Bundle()
        onWriteToBundle(bundle)
        parcel.writeBundle(bundle)
        checks.recycle()
    }

    companion object {
        @JvmField
        public val CREATOR = object : Parcelable.Creator<DataMap<Any?>> {

            override fun createFromParcel(parcel: Parcel): DataMap<Any?> {
                val klass = Class.forName(parcel.readString())
                @Suppress("UNCHECKED_CAST")
                val dataMap = klass.newInstance() as DataMap<Any?>
                val dirty = Array(parcel.readInt(), { "" })
                parcel.readStringArray(dirty)
                val count = parcel.readInt()
                val values = HashMap<String, Any?>(count)
                repeat(count) {
                    val key = parcel.readString()
                    val what = parcel.readString()
                    values[key] = when {
                        what == "value" -> parcel.readValue(dataMap::class.java.classLoader)
                        else -> {
                            val className = parcel.readString()
                            @Suppress("UNCHECKED_CAST")
                            val parceler = DataDocument.PARCELERS[className] as? DataDocument.Parceler<Any?>
                            if (parceler == null) throw IllegalStateException("Can not parcel type $className. Please register a parceler using DataDocument.registerParceler.")
                            parceler.create(parcel)
                        }
                    }
                }

                dataMap.dirty.clear()
                dataMap.dirty.addAll(dirty)
                dataMap.data.clear()
                dataMap.data.putAll(values)
                val bundle = parcel.readBundle(dataMap::class.java.classLoader)
                dataMap.onReadFromBundle(bundle)
                return dataMap
            }

            override fun newArray(size: Int): Array<DataMap<Any?>?> {
                return Array(size, { null })
            }
        }
    }

    protected open fun onWriteToBundle(bundle: Bundle) {}

    protected open fun onReadFromBundle(bundle: Bundle) {}
}