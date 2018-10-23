package com.otaliastudios.firestore.compiler

import com.otaliastudios.firestore.FirestoreMetadata
import com.squareup.kotlinpoet.*
import javax.annotation.processing.Filer
import javax.tools.StandardLocation

class Writer(private val filer: Filer) {

    fun write(className: String, packageName: String, map: Map<String, PropertyInfo>, innerType: String) {

        val createFunction = FunSpec.builder("create").apply {
            addModifiers(KModifier.OVERRIDE, KModifier.PUBLIC)
            addParameter("key", String::class)
            val type = TypeVariableName.invoke("T")
            addTypeVariable(type)
            returns(type.asNullable())

            val codeBuilder = CodeBlock.builder().beginControlFlow("return when (key)")
            map.forEach { key, returnType ->
                codeBuilder.addStatement("\"$key\" -> ${getDefaultConstructorStatement(returnType.returnClassName)}")
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
            addStatement("return ${getDefaultConstructorStatement(innerType)}")
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
                codeBuilder.addStatement("\"$key\" -> ${Classes.DATABINDING_BR}.$key")
            }
            codeBuilder.addStatement("else -> null")
            codeBuilder.endControlFlow()
            addCode(codeBuilder.build())
        }

        val classBuilder = TypeSpec.classBuilder(className).apply {
            addSuperinterface(FirestoreMetadata::class)
            addFunction(createFunction.build())
            addFunction(isNullableFunction.build())
            addFunction(getBindableResourceFunction.build())
            addFunction(createInnerTypeFunction.build())
            addAnnotation(AnnotationSpec.builder(Classes.SUPPRESS)
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

    /**
     * Returns
     */
    private fun getDefaultConstructorStatement(type: String): String {
        return when (type) {
            Classes.KOTLIN_INT -> "0 as T"
            Classes.KOTLIN_FLOAT -> "0F as T"
            Classes.KOTLIN_DOUBLE -> "0.0 as T"
            Classes.KOTLIN_STRING -> "\"\" as T"
            Classes.TIMESTAMP -> "$type(0L, 0) as T"
            else -> return "$type::class.java.getConstructor().newInstance() as T"
        }
    }
}