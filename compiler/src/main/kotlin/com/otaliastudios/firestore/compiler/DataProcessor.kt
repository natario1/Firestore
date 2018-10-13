/*
 * Copyright (c) 2018 Otalia Studios. Author: Mattia Iavarone.
 */

package com.otaliastudios.firestore.compiler

import com.otaliastudios.firestore.DataClass
import com.otaliastudios.firestore.DataMetadata
import com.squareup.kotlinpoet.*
import kotlinx.metadata.*
import kotlinx.metadata.ClassName
import kotlinx.metadata.impl.extensions.MetadataExtensions
import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.lang.model.type.DeclaredType
import javax.tools.Diagnostic
import javax.tools.StandardLocation

// app/build/generated/source/kapt/debug/my/package/Class_Metadata.kt
class DataProcessor : AbstractProcessor() {

    @Suppress("UNCHECKED_CAST")
    companion object {

        // Should not be needed anymore.
        /* init {
            // Bug fix for 0.0.2: https://youtrack.jetbrains.com/issue/KT-24881
            with(Thread.currentThread()) {
                val classLoader = contextClassLoader
                contextClassLoader = MetadataExtensions::class.java.classLoader
                try {
                    MetadataExtensions.INSTANCES
                } finally {
                    contextClassLoader = classLoader
                }
            }
        } */

        val FIRESTORE_EXCLUDE = Class.forName("com.google.firebase.firestore.Exclude") as Class<Annotation>
        val NULLABLE = Class.forName("org.jetbrains.annotations.Nullable") as Class<Annotation>
        val SUPPRESS = Class.forName("kotlin.Suppress") as Class<Annotation>
        val BINDABLE = Class.forName("androidx.databinding.Bindable") as Class<Annotation>

        const val DATA_MAP = "com.otaliastudios.firestore.DataMap"
        const val DATA_LIST = "com.otaliastudios.firestore.DataList"
        const val DATABINDING_BR = "androidx.databinding.library.baseAdapters.BR"
    }


    private fun ClassName.readable(): String {
        return toString().replace('/', '.')
    }

    private fun ClassName.isDataMapOrDataList(): Boolean {
        val qualifiedClassName = readable()
        return qualifiedClassName == DATA_MAP || qualifiedClassName == DATA_LIST
    }

    private fun DeclaredType.isDataMapOrDataList(): Boolean {
        val qualifiedClassName = getQualifiedClassName()
        return qualifiedClassName == DATA_MAP || qualifiedClassName == DATA_LIST
    }

    private fun DeclaredType.getSuperDeclaredType(): DeclaredType {
        return (asElement() as TypeElement).superclass as DeclaredType
    }

    private fun DeclaredType.getQualifiedClassName(): String {
        return (asElement() as TypeElement).qualifiedName.toString()
    }

    private fun Messager.print(message: String) {
        printMessage(Diagnostic.Kind.NOTE, message)
    }

    private lateinit var messager: Messager
    private lateinit var filer: Filer

    override fun init(environment: ProcessingEnvironment) {
        super.init(environment)
        messager = environment.messager
        filer = environment.filer
    }

    override fun process(set: Set<TypeElement>, environment: RoundEnvironment): Boolean {
        val elements = environment.getElementsAnnotatedWith(DataClass::class.java)
        for (element in elements) {
            if (element.kind != ElementKind.CLASS) continue
            val header = element.readHeader()!!
            val metadata = KotlinClassMetadata.read(header) as KotlinClassMetadata.Class
            metadata.accept(processClass(element as TypeElement))
        }
        return true
    }

    class PropertyInfo(val isBindable: Boolean,
                       val returnClassName: String,
                       val returnIsNullable: Boolean)

    /**
     * We need a map from declared property to return type, this way we can:
     * - instantiate a default value when get() is called
     * - instantiate the correct DataMap or DataList subclass when we find one from declared properties
     */
    private fun processClass(element: TypeElement): KmClassVisitor {
        val map = mutableMapOf<String, PropertyInfo>()
        val className = element.simpleName.toString()
        messager.print("Processing class: $className")

        // Find the package name.
        var enclosing = element.enclosingElement
        while (enclosing.kind != ElementKind.PACKAGE) {
            enclosing = enclosing.enclosingElement
        }
        val packageName = (enclosing as PackageElement).qualifiedName.toString()
        messager.print("Found package name: $packageName")

        // Cycle up the superclasses until we find either DataMap or DataList.
        // We are interested in their type parameter.
        var parent = element.asType() as DeclaredType
        while (!parent.isDataMapOrDataList()) {
            parent = parent.getSuperDeclaredType()
        }
        (parent.typeArguments.first() as DeclaredType)
        val type = parent.typeArguments.first().toString()
        messager.print("Found DataMap/DataList parameter: $type")

        // Visit the class itself using Kotlin metadata.
        // Cycle through properties to get default values and bindable references.
        return object : KmClassVisitor() {

            override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? {
                val javaProperty = element.enclosedElements.first {
                    // TODO check that we are not private
                    it.simpleName.toString().toLowerCase() == "get${name.toLowerCase()}" && it.kind == ElementKind.METHOD
                }

                // Checks the exclude property on the getter
                val hasExclude = javaProperty.getAnnotation(FIRESTORE_EXCLUDE) != null
                val hasNullable = javaProperty.getAnnotation(NULLABLE) != null
                val hasBindable = javaProperty.getAnnotation(BINDABLE) != null

                // Visit the return type
                return if (hasExclude) null else object : KmPropertyVisitor() {
                    override fun visitReturnType(flags: Flags): KmTypeVisitor? {
                        // Crashes, bug - val nullable = Flag.Type.IS_NULLABLE(flags)
                        return returnTypeVisitor(name, hasNullable, hasBindable, map)
                    }
                }
            }

            override fun visitEnd() {
                super.visitEnd()
                writeClass(className + DataMetadata.SUFFIX, packageName, map, type)
            }
        }
    }

    private fun returnTypeVisitor(key: String, nullable: Boolean, bindable: Boolean, map: MutableMap<String, PropertyInfo>) = object: KmTypeVisitor() {

        private var isDataMapOrDataList: Boolean = false
        private var returnClassName: String = ""

        override fun visitClass(name: ClassName) {
            returnClassName = name.readable()
            isDataMapOrDataList = name.isDataMapOrDataList()
        }

        override fun visitArgument(flags: Flags, variance: KmVariance): KmTypeVisitor? {
            return if (!isDataMapOrDataList) null else object: KmTypeVisitor() {
                // This is either DataList or DataMap. This function will be called only once.
                // This also means that the class will not go through the DataProcessor.
                // So we must inspect generics here.

            }
        }

        override fun visitEnd() {
            super.visitEnd()
            map[key] = PropertyInfo(
                    returnClassName = returnClassName,
                    returnIsNullable = nullable,
                    isBindable = bindable
            )
        }

    }

    private fun writeClass(className: String, packageName: String, map: Map<String, PropertyInfo>, innerType: String) {

        val createFunction = FunSpec.builder("create").apply {
            addModifiers(KModifier.OVERRIDE, KModifier.PUBLIC)
            addParameter("key", String::class)
            val type = TypeVariableName.invoke("T")
            addTypeVariable(type)
            returns(type.asNullable())

            val codeBuilder = CodeBlock.builder().beginControlFlow("return when (key)")
            map.forEach { key, returnType ->
                codeBuilder.addStatement("\"$key\" -> ${returnType.returnClassName}::class.java.getConstructor().newInstance() as T")
            }
            codeBuilder.addStatement("else -> null")
            codeBuilder.endControlFlow()
            addCode(codeBuilder.build())
        }

        val createInnerTypeFunction = FunSpec.builder("createInnerType").apply {
            addModifiers(KModifier.OVERRIDE, KModifier.PUBLIC)
            val type = TypeVariableName.invoke("T")
            addTypeVariable(type)
            returns(type.asNullable())
            addStatement("return $innerType::class.java.getConstructor().newInstance() as T")
        }

        val isNullableFunction = FunSpec.builder("isNullable").apply {
            addModifiers(KModifier.OVERRIDE, KModifier.PUBLIC)
            addParameter("key", String::class)
            returns(Boolean::class)

            val codeBuilder = CodeBlock.builder().beginControlFlow("return when (key)")
            map.forEach { key, returnType ->
                codeBuilder.addStatement("\"$key\" -> ${returnType.returnIsNullable}")
            }
            codeBuilder.addStatement("else -> false")
            codeBuilder.endControlFlow()
            addCode(codeBuilder.build())
        }

        val getBindableResourceFunction = FunSpec.builder("getBindableResource").apply {
            addModifiers(KModifier.OVERRIDE, KModifier.PUBLIC)
            addParameter("key", String::class)
            returns(Int::class.asTypeName().asNullable())

            val codeBuilder = CodeBlock.builder().beginControlFlow("return when (key)")
            map.filter { it.value.isBindable }.forEach { key, _ ->
                codeBuilder.addStatement("\"$key\" -> $DATABINDING_BR.$key")
            }
            codeBuilder.addStatement("else -> null")
            codeBuilder.endControlFlow()
            addCode(codeBuilder.build())
        }

        val classBuilder = TypeSpec.classBuilder(className).apply {
            addSuperinterface(DataMetadata::class)
            addFunction(createFunction.build())
            addFunction(isNullableFunction.build())
            addFunction(getBindableResourceFunction.build())
            addFunction(createInnerTypeFunction.build())
            addAnnotation(AnnotationSpec.builder(SUPPRESS)
                    .addMember("\"UNCHECKED_CAST\"")
                    .addMember("\"UNUSED_EXPRESSION\"").build())
        }

        val fileBuilder = FileSpec.builder(packageName, className).apply {
            addComment("Generated file.")
            addType(classBuilder.build())
        }

        val file = filer.createResource(StandardLocation.SOURCE_OUTPUT, packageName, "$className.kt")
        file.openWriter().use {
            fileBuilder.build().writeTo(it)
        }
    }

    override fun getSupportedAnnotationTypes(): MutableSet<String> {
        return mutableSetOf(DataClass::class.qualifiedName!!)
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.RELEASE_8
    }

    // https://github.com/JetBrains/kotlin/tree/master/libraries/kotlinx-metadata/jvm
    // https://github.com/square/moshi/pull/570/files
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    private fun Element.readHeader(): KotlinClassHeader? {
        return getAnnotation(Metadata::class.java)?.run {
            KotlinClassHeader(k, mv, bv, d1, d2, xs, pn, xi)
        }
    }
}
