/*
 * Copyright (c) 2018 Otalia Studios. Author: Mattia Iavarone.
 */

package com.otaliastudios.firestore

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DataClass

public interface DataMetadata {
    fun <T> create(key: String): T?
    fun isNullable(key: String): Boolean
    fun getBindableResource(key: String): Int?
    fun <T> createInnerType(): T?

    companion object {
        const val SUFFIX = "MetadataImpl"
    }
}