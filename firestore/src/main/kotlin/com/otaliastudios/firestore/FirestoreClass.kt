/*
 * Copyright (c) 2018 Otalia Studios. Author: Mattia Iavarone.
 */

package com.otaliastudios.firestore

import androidx.annotation.Keep

/**
 * Identifies [FirestoreDocument], [FirestoreMap]s and [FirestoreList]s
 * so that they can be processed by the annotation processor.
 */
@Keep
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class FirestoreClass
// Keep in sync with compiler!