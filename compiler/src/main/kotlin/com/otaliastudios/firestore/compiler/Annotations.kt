/*
 * Copyright (c) 2018 Otalia Studios. Author: Mattia Iavarone.
 */

@file:Suppress("PackageDirectoryMismatch")
package com.otaliastudios.firestore

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class FirestoreClass


public interface FirestoreMetadata {
    fun <T> create(key: String): T?
    fun isNullable(key: String): Boolean
    fun getBindableResource(key: String): Int?
    fun <T> createInnerType(): T?

    companion object {
        const val SUFFIX = "MetadataImpl"
    }
}