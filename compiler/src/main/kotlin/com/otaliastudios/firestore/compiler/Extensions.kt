package com.otaliastudios.firestore.compiler

import kotlinx.metadata.ClassName
import kotlinx.metadata.jvm.KotlinClassHeader
import javax.annotation.processing.Messager
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.tools.Diagnostic

fun ClassName.readable(): String {
    return toString().replace('/', '.')
}

fun ClassName.isFirestoreMapOrFirestoreList(): Boolean {
    val qualifiedClassName = readable()
    return qualifiedClassName == Classes.FIRESTORE_MAP || qualifiedClassName == Classes.FIRESTORE_LIST
}

fun DeclaredType.isFirestoreMapOrFirestoreList(): Boolean {
    val qualifiedClassName = getQualifiedClassName()
    return qualifiedClassName == Classes.FIRESTORE_MAP || qualifiedClassName == Classes.FIRESTORE_LIST
}

fun DeclaredType.isFirestoreDocument(): Boolean {
    return getQualifiedClassName() == Classes.FIRESTORE_DOCUMENT
}

fun DeclaredType.getSuperDeclaredType(): DeclaredType {
    return (asElement() as TypeElement).superclass as DeclaredType
}

fun DeclaredType.getQualifiedClassName(): String {
    return (asElement() as TypeElement).qualifiedName.toString()
}

fun Messager.print(message: String) {
    printMessage(Diagnostic.Kind.NOTE, message)
}

// https://github.com/JetBrains/kotlin/tree/master/libraries/kotlinx-metadata/jvm
// https://github.com/square/moshi/pull/570/files
fun Element.readHeader(): KotlinClassHeader? {
    return getAnnotation(Metadata::class.java)?.run {
        KotlinClassHeader(kind,
                metadataVersion,
                bytecodeVersion,
                data1,
                data2,
                extraString,
                packageName,
                extraInt
        )
    }
}