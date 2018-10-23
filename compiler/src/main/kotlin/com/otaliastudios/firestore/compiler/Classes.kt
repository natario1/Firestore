package com.otaliastudios.firestore.compiler

@Suppress("UNCHECKED_CAST")
object Classes {

    const val FIRESTORE_DOCUMENT = "com.otaliastudios.firestore.FirestoreDocument"
    const val FIRESTORE_MAP = "com.otaliastudios.firestore.FirestoreMap"
    const val FIRESTORE_LIST = "com.otaliastudios.firestore.FirestoreList"
    const val DATABINDING_BR = "androidx.databinding.library.baseAdapters.BR"

    const val TIMESTAMP = "com.google.firebase.Timestamp"

    const val KOTLIN_INT = "kotlin.Int"
    const val KOTLIN_FLOAT = "kotlin.Float"
    const val KOTLIN_DOUBLE = "kotlin.Double"
    const val KOTLIN_STRING = "kotlin.String"

    val FIRESTORE_EXCLUDE = Class.forName("com.google.firebase.firestore.Exclude") as Class<Annotation>
    val NULLABLE = Class.forName("org.jetbrains.annotations.Nullable") as Class<Annotation>
    val SUPPRESS = Class.forName("kotlin.Suppress") as Class<Annotation>
    val BINDABLE = Class.forName("androidx.databinding.Bindable") as Class<Annotation>
}