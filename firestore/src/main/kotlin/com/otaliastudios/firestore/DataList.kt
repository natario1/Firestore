/*
 * Copyright (c) 2018 Otalia Studios. Author: Mattia Iavarone.
 */

package com.otaliastudios.firestore

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import com.google.firebase.firestore.Exclude

/**
 * A list implementation. Delegates to a mutable list.
 *
 * The point of list is dirtyness.
 *
 * When an item is inserted, removed, or when a inner DataMap/List is changed,
 * this list should be marked as dirty.
 */
open class DataList<T: Any> @JvmOverloads constructor(
        source: List<T>? = null
) : /* ObservableList<T>, MutableList<T> by data, */Iterable<T>, Parcelable {

    private val data: MutableList<T> = mutableListOf()

    @get:Exclude
    val size = data.size

    init {
        if (source != null) {
            mergeValues(source)
        }
    }

    override fun iterator(): Iterator<T> {
        return data.iterator()
    }

    private var isDirty = false

    internal fun collectDirtyValues(map: MutableMap<String, Any?>, prefix: String) {
        if (isDirty()) {
            collectAllValues(map, prefix)
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal fun collectAllValues(map: MutableMap<String, Any?>, prefix: String) {
        val list = mutableListOf<T>()
        forEach {
            if (it is DataMap<*>) {
                val cleanMap = mutableMapOf<String, Any?>()
                it.collectAllValues(cleanMap, "")
                list.add(cleanMap as T)
            } else if (it is DataList<*>) {
                val cleanMap = mutableMapOf<String, Any?>()
                it.collectAllValues(cleanMap, "")
                list.add(cleanMap as T)
            } else {
                list.add(it)
            }
        }
         map[prefix] = list
    }

    @Suppress("UNCHECKED_CAST")
    fun toSafeList(): List<T> {
        val map = mutableMapOf<String, Any?>()
        collectAllValues(map, "data")
        return map["data"] as List<T>
    }

    internal fun isDirty(): Boolean {
        if (isDirty) return true
        for (it in this) {
            if (it is DataList<*> && it.isDirty()) return true
            if (it is DataMap<*> && it.isDirty()) return true
        }
        return false
    }

    internal fun clearDirt() {
        isDirty = false
        for (it in this) {
            if (it is DataList<*>) it.clearDirt()
            if (it is DataMap<*>) it.clearDirt()
        }
    }

    private fun <K> createDataMap(): DataMap<K> {
        val map = try { onCreateDataMap<K>() } catch (e: Exception) {
            DataMap<K>()
        }
        map.clearDirt()
        return map
    }

    private fun <K: Any> createDataList(): DataList<K> {
        val list = try { onCreateDataList<K>() } catch (e: Exception) {
            DataList<K>()
        }
        list.clearDirt()
        return list
    }

    protected open fun <K> onCreateDataMap(): DataMap<K> {
        val provider = DataDocument.metadataProvider(this::class)
        return provider.createInnerType<DataMap<K>>() ?: DataMap()
    }

    protected open fun <K: Any> onCreateDataList(): DataList<K> {
        val provider = DataDocument.metadataProvider(this::class)
        return provider.createInnerType<DataList<K>>() ?: DataList()
    }

    @Suppress("UNCHECKED_CAST")
    internal fun mergeValues(values: List<T>) {
        data.clear()
        for (value in values) {
            if (value is Map<*, *> && value.keys.all { it is String }) {
                val child = createDataMap<Any?>() as T
                data.add(child)
                child as DataMap<Any?>
                value as Map<String, Any?>
                child.mergeValues(value)
            } else if (value is List<*>) {
                val child = createDataList<Any>() as T
                data.add(child)
                child as DataList<Any>
                value as List<Any>
                child.mergeValues(value)
            } else {
                data.add(value)
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is DataList<*> &&
                other.data.size == data.size &&
                other.data.containsAll(data) &&
                other.isDirty == isDirty
    }

    override fun hashCode(): Int {
        var result = data.hashCode()
        result = 31 * result + isDirty.hashCode()
        return result
    }

    // Parcelable stuff.

    override fun describeContents() = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(this::class.java.name)
        parcel.writeInt(if (isDirty) 1 else 0)
        parcel.writeInt(size)
        val checks = Parcel.obtain()
        for (value in data) {
            val canWrite = try {
                checks.writeValue(value)
                true
            } catch (e: Exception) { false }
            if (canWrite) {
                parcel.writeString("value")
                parcel.writeValue(value)
            } else {
                val className = value::class.java.name
                parcel.writeString(className)
                @Suppress("UNCHECKED_CAST")
                val parceler = DataDocument.PARCELERS[className] as? DataDocument.Parceler<Any?>
                if (parceler == null) throw IllegalStateException("Can not parcel type ${value.javaClass}. Please register a parceler using DataDocument.registerParceler.")
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
        public val CREATOR = object : Parcelable.Creator<DataList<Any>> {

            override fun createFromParcel(parcel: Parcel): DataList<Any> {
                val klass = Class.forName(parcel.readString())
                @Suppress("UNCHECKED_CAST")
                val dataList = klass.newInstance() as DataList<Any>
                dataList.isDirty = parcel.readInt() == 1
                val count = parcel.readInt()
                repeat(count) {
                    val what = parcel.readString()
                    dataList.data.add(when {
                        what == "value" -> parcel.readValue(dataList::class.java.classLoader)
                        else -> {
                            val className = parcel.readString()
                            @Suppress("UNCHECKED_CAST")
                            val parceler = DataDocument.PARCELERS[className] as? DataDocument.Parceler<Any>
                            if (parceler == null) throw IllegalStateException("Can not parcel type $className. Please register a parceler using DataDocument.registerParceler.")
                            parceler.create(parcel)
                        }
                    })
                }

                val bundle = parcel.readBundle(dataList::class.java.classLoader)
                dataList.onReadFromBundle(bundle)
                return dataList
            }

            override fun newArray(size: Int): Array<DataList<Any>?> {
                return Array(size, { null })
            }
        }
    }

    protected open fun onWriteToBundle(bundle: Bundle) {}

    protected open fun onReadFromBundle(bundle: Bundle) {}

    // Dirtyness stuff.

    fun add(element: T): Boolean {
        data.add(element)
        isDirty = true
        return true
    }

    fun add(index: Int, element: T) {
        data.add(index, element)
        isDirty = true
    }

    fun remove(element: T): Boolean {
        val result = data.remove(element)
        isDirty = true
        return result
    }

    operator fun set(index: Int, element: T): T {
        val value = data.set(index, element)
        isDirty = true
        return value
    }

    // ObservableArrayList stuff.
    /*
    private var registry: ListChangeRegistry = ListChangeRegistry()

    override fun addOnListChangedCallback(callback: ObservableList.OnListChangedCallback<out ObservableList<T>>?) {
        registry.add(callback)
    }

    override fun removeOnListChangedCallback(callback: ObservableList.OnListChangedCallback<out ObservableList<T>>?) {
        registry.remove(callback)
    }

    override fun clear() {
        val oldSize = size
        data.clear()
        if (oldSize != 0) {
            registry.notifyRemoved(this, 0, oldSize)
            isDirty = true
        }
    }

    override fun add(element: T): Boolean {
        data.add(element)
        registry.notifyInserted(this, size - 1, 1)
        isDirty = true
        return true
    }

    override fun add(index: Int, element: T) {
        data.add(index, element)
        registry.notifyInserted(this, index, 1)
        isDirty = true
    }

    override fun addAll(elements: Collection<T>): Boolean {
        val oldSize = size
        val added = data.addAll(elements)
        if (added) {
            registry.notifyInserted(this, oldSize, size - oldSize)
            isDirty = true
        }
        return added
    }

    override fun addAll(index: Int, elements: Collection<T>): Boolean {
        val added = data.addAll(index, elements)
        if (added) {
            registry.notifyInserted(this, index, elements.size)
            isDirty = true
        }
        return added
    }

    override fun remove(element: T): Boolean {
        val index = indexOf(element)
        if (index >= 0) {
            removeAt(index)
            return true
        } else {
            return false
        }
    }

    override fun removeAt(index: Int): T {
        val value = data.removeAt(index)
        registry.notifyRemoved(this, index, 1)
        isDirty = true
        return value
    }

    override fun set(index: Int, element: T): T {
        val value = data.set(index, element)
        registry.notifyChanged(this, index, 1)
        isDirty = true
        return value
    } */
}