/*
 * Copyright (c) 2018 Otalia Studios. Author: Mattia Iavarone.
 */

package com.otaliastudios.firestore.compiler

import com.otaliastudios.firestore.FirestoreClass
import com.otaliastudios.firestore.FirestoreMetadata
import com.otaliastudios.firestore.compiler.Types.BINDABLE
import com.otaliastudios.firestore.compiler.Types.FIRESTORE_CLASS
import com.otaliastudios.firestore.compiler.Types.FIRESTORE_DOCUMENT
import com.otaliastudios.firestore.compiler.Types.FIRESTORE_EXCLUDE
import com.otaliastudios.firestore.compiler.Types.FIRESTORE_LIST
import com.otaliastudios.firestore.compiler.Types.FIRESTORE_MAP
import com.otaliastudios.firestore.compiler.Types.KOTLIN_BOOLEAN
import com.otaliastudios.firestore.compiler.Types.KOTLIN_DOUBLE
import com.otaliastudios.firestore.compiler.Types.KOTLIN_FLOAT
import com.otaliastudios.firestore.compiler.Types.KOTLIN_INT
import com.otaliastudios.firestore.compiler.Types.KOTLIN_STRING
import com.otaliastudios.firestore.compiler.Types.NULLABLE
import com.otaliastudios.firestore.compiler.Types.SUPPRESS
import com.otaliastudios.firestore.compiler.Types.TIMESTAMP
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.metadata.KotlinPoetMetadataPreview
import com.squareup.kotlinpoet.metadata.specs.toTypeSpec
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.lang.model.type.DeclaredType
import javax.tools.Diagnostic

@KotlinPoetMetadataPreview
@SupportedAnnotationTypes(FIRESTORE_CLASS)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
class Processor : AbstractProcessor() {

    override fun process(set: Set<TypeElement>, environment: RoundEnvironment): Boolean {
        collect(environment).forEach {
            val properties = inspect(it)
            write(it, properties)
        }
        return true
    }

    private fun collect(environment: RoundEnvironment): List<TypeElement> {
        return environment
                .getElementsAnnotatedWith(FirestoreClass::class.java)
                .mapNotNull {
                    if (it.kind != ElementKind.CLASS) {
                        logError("FirestoreClass should only annotate classes.", it)
                        null
                    } else {
                        it as TypeElement
                    }
                }
    }

    private fun inspect(element: TypeElement): List<Property> {
        logInfo("Inspecting ${element.simpleName}")

        // Init properties. If document, add extra timestamps.
        val properties = mutableListOf<Property>()
        if (element.isFirestoreDocument) {
            logInfo("It's a document! Adding timestamp fields.")
            properties.add(Property("createdAt", TIMESTAMP, isNullable = true))
            properties.add(Property("updatedAt", TIMESTAMP, isNullable = true))
        }

        // Inspect properties.
        val spec = element.toTypeSpec()
        spec.propertySpecs.forEach { property ->
            logInfo("Inspecting property ${property.name} of type ${property.type}.")
            val annotationsSpecs = property.annotations + (property.getter?.annotations ?: listOf())
            val annotations = annotationsSpecs.map { it.className.canonicalName }
            if (annotations.contains(FIRESTORE_EXCLUDE)) return@forEach
            val isNullable = property.type.isNullable || annotations.contains(NULLABLE)
            val isBindable = annotations.contains(BINDABLE)
            properties.add(Property(
                    name = property.name,
                    type = property.type.copy(nullable = false, annotations = listOf()).toString(),
                    isBindable = isBindable,
                    isNullable = isNullable)
            )
        }
        return properties
    }

    private fun write(element: TypeElement, properties: List<Property>) {
        // Collect file info
        val packageName = element.packageName
        val innerType = element.firestoreAncestor.typeArguments.first().toString()
        val inputClass = element.simpleName.toString()
        val outputClass = "$inputClass${FirestoreMetadata.SUFFIX}"
        logInfo("Writing... package=$packageName, innerType=$innerType")

        // Create function
        val createFunction = FunSpec.builder("create").apply {
            addModifiers(KModifier.OVERRIDE, KModifier.PUBLIC)
            addParameter("key", String::class)
            val type = TypeVariableName.invoke("T")
            addTypeVariable(type)
            returns(type.copy(nullable = true))
            val codeBuilder = CodeBlock.builder().beginControlFlow("return when (key)")
            properties.forEach {
                codeBuilder.addStatement("\"${it.name}\" -> ${getNewInstanceStatement(it.type)}")
            }
            codeBuilder.addStatement("else -> null")
            codeBuilder.endControlFlow()
            addCode(codeBuilder.build())
        }

        // Create inner type function
        val createInnerTypeFunction = FunSpec.builder("createInnerType").apply {
            addModifiers(KModifier.OVERRIDE, KModifier.PUBLIC)
            val type = TypeVariableName.invoke("T")
            addTypeVariable(type)
            returns(type.copy(nullable = true))
            addStatement("return ${getNewInstanceStatement(innerType)}")
        }

        // Nullable function
        val isNullableFunction = FunSpec.builder("isNullable").apply {
            addModifiers(KModifier.OVERRIDE, KModifier.PUBLIC)
            addParameter("key", String::class)
            returns(Boolean::class)

            val codeBuilder = CodeBlock.builder().beginControlFlow("return when (key)")
            properties.forEach {
                codeBuilder.addStatement("\"${it.name}\" -> ${it.isNullable}")
            }
            codeBuilder.addStatement("else -> false")
            codeBuilder.endControlFlow()
            addCode(codeBuilder.build())
        }

        // Get bindable resource function
        val getBindableResourceFunction = FunSpec.builder("getBindableResource").apply {
            addModifiers(KModifier.OVERRIDE, KModifier.PUBLIC)
            addParameter("key", String::class)
            returns(Int::class.asTypeName().copy(nullable = true))
            val codeBuilder = CodeBlock.builder().beginControlFlow("return when (key)")
            properties.filter { it.isBindable }.forEach {
                codeBuilder.addStatement("\"${it.name}\" -> ${Types.DATABINDING_BR}.${it.name}")
            }
            codeBuilder.addStatement("else -> null")
            codeBuilder.endControlFlow()
            addCode(codeBuilder.build())
        }

        // Merge them together
        val classBuilder = TypeSpec.classBuilder(outputClass).apply {
            addSuperinterface(FirestoreMetadata::class)
            addFunction(createFunction.build())
            addFunction(isNullableFunction.build())
            addFunction(getBindableResourceFunction.build())
            addFunction(createInnerTypeFunction.build())
            @Suppress("UNCHECKED_CAST")
            val suppress = Class.forName(SUPPRESS) as Class<Annotation>
            addAnnotation(AnnotationSpec.builder(suppress)
                    .addMember("\"UNCHECKED_CAST\"")
                    .addMember("\"UNUSED_EXPRESSION\"").build())
        }

        // Write file
        // app/build/generated/source/kapt/debug/my/package/Class_Metadata.kt
        FileSpec.builder(packageName, outputClass)
                .addComment("Generated file.")
                .addType(classBuilder.build())
                .build()
                .writeTo(processingEnv.filer)
    }

    private val Element.packageName: String
        get() {
            var element = this
            while (element.kind != ElementKind.PACKAGE) {
                element = element.enclosingElement
            }
            return (element as PackageElement).toString()
        }

    private val TypeElement.firestoreAncestor: DeclaredType
        get() {
            var element = this
            var declaredType: DeclaredType?
            while (true) {
                declaredType = element.superclass as DeclaredType
                element = declaredType.asElement() as TypeElement
                val name = element.qualifiedName.toString()
                if (name == FIRESTORE_MAP || name == FIRESTORE_LIST) {
                    break
                }
            }
            logInfo("Ancestor final type: ${element.simpleName}")
            return declaredType!!
        }

    private val TypeElement.isFirestoreDocument: Boolean
        get() {
            var isDocument: Boolean
            var element = this
            while (true) {
                val name = element.qualifiedName.toString()
                isDocument = name == FIRESTORE_DOCUMENT
                if (isDocument || name == FIRESTORE_LIST || name == FIRESTORE_MAP) {
                    // TODO also break if Any
                    break
                }
                element = (element.superclass as DeclaredType).asElement() as TypeElement
            }
            return isDocument
        }

    private fun getNewInstanceStatement(type: String): String {
        return when (type) {
            KOTLIN_INT -> "0 as T"
            KOTLIN_FLOAT -> "0F as T"
            KOTLIN_DOUBLE -> "0.0 as T"
            KOTLIN_STRING -> "\"\" as T"
            KOTLIN_BOOLEAN -> "false as T"
            TIMESTAMP -> "$type(0L, 0) as T"
            else -> return "$type::class.java.getConstructor().newInstance() as T"
        }
    }

    @Suppress("SameParameterValue")
    private fun logError(message: String, element: Element?) {
        processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, message, element)
    }

    private fun logInfo(message: String) {
        processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, message)
    }
}
