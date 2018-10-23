/*
 * Copyright (c) 2018 Otalia Studios. Author: Mattia Iavarone.
 */

package com.otaliastudios.firestore.compiler

import com.otaliastudios.firestore.FirestoreClass
import com.otaliastudios.firestore.FirestoreMetadata
import com.squareup.kotlinpoet.*
import kotlinx.metadata.*
import kotlinx.metadata.ClassName
import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.lang.model.type.DeclaredType
import javax.tools.Diagnostic
import javax.tools.StandardLocation

// app/build/generated/source/kapt/debug/my/package/Class_Metadata.kt
class Processor : AbstractProcessor() {

    private lateinit var messager: Messager
    private lateinit var writer: Writer

    override fun getSupportedAnnotationTypes(): MutableSet<String> {
        return mutableSetOf(FirestoreClass::class.qualifiedName!!)
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.RELEASE_8
    }

    override fun init(environment: ProcessingEnvironment) {
        super.init(environment)
        messager = environment.messager
        writer = Writer(environment.filer)
    }

    override fun process(set: Set<TypeElement>, environment: RoundEnvironment): Boolean {
        val elements = environment.getElementsAnnotatedWith(FirestoreClass::class.java)
        for (element in elements) {
            if (element.kind != ElementKind.CLASS) continue
            val header = element.readHeader()!!
            val metadata = KotlinClassMetadata.read(header) as KotlinClassMetadata.Class
            val typeElement = element as TypeElement
            metadata.accept(ClassVisitor(writer, messager, typeElement))
        }
        return true
    }
}
