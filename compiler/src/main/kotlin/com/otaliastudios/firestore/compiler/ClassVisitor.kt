package com.otaliastudios.firestore.compiler

import com.otaliastudios.firestore.FirestoreMetadata
import kotlinx.metadata.*
import javax.annotation.processing.Messager
import javax.lang.model.element.ElementKind
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType

/**
 * We need a map from declared property to return type, this way we can:
 * - instantiate a default value when get() is called
 * - instantiate the correct FirestoreMap or FirestoreList subclass when we find one from declared properties
 */
class ClassVisitor(
        private val writer: Writer,
        private val messager: Messager,
        private val element: TypeElement
) : KmClassVisitor() {

    private val map = mutableMapOf<String, PropertyInfo>()
    private val className = element.simpleName.toString()
    private val packageName: String
    private val type: String

    init {
        messager.print("Processing class: $className")

        // Find the package name.
        var enclosing = element.enclosingElement
        while (enclosing.kind != ElementKind.PACKAGE) {
            enclosing = enclosing.enclosingElement
        }
        packageName = (enclosing as PackageElement).qualifiedName.toString()
        messager.print("Found package name: $packageName")

        // Cycle up the superclasses until we find either FirestoreMap or FirestoreList.
        // We are interested in their type parameter.
        var parent = element.asType() as DeclaredType
        var isDocument = false
        while (!parent.isFirestoreMapOrFirestoreList()) {
            if (!isDocument && parent.isFirestoreDocument()) {
                isDocument = true
            }
            parent = parent.getSuperDeclaredType()
        }
        type = parent.typeArguments.first().toString()
        messager.print("Found FirestoreMap/FirestoreList parameter: $type")

        if (isDocument) {
            messager.print("It is also a document. adding createdAt and updatedAt.")
            map["createdAt"] = PropertyInfo(false, Classes.TIMESTAMP, true)
            map["updatedAt"] = PropertyInfo(false, Classes.TIMESTAMP, true)
        }
    }

    override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? {
        val javaProperty = element.enclosedElements.first {
            // TODO check that we are not private
            it.simpleName.toString().toLowerCase() == "get${name.toLowerCase()}" && it.kind == ElementKind.METHOD
        }

        // Checks the exclude property on the getter
        val hasExclude = javaProperty.getAnnotation(Classes.FIRESTORE_EXCLUDE) != null
        val hasNullable = javaProperty.getAnnotation(Classes.NULLABLE) != null
        val hasBindable = javaProperty.getAnnotation(Classes.BINDABLE) != null

        // Visit the return type
        return if (hasExclude) null else object : KmPropertyVisitor() {
            override fun visitReturnType(flags: Flags): KmTypeVisitor? {
                // Crashes, bug - val nullable = Flag.Type.IS_NULLABLE(flags)
                return ReturnTypeVisitor(name, hasNullable, hasBindable, map)
            }
        }
    }

    override fun visitEnd() {
        super.visitEnd()
        writer.write(className + FirestoreMetadata.SUFFIX, packageName, map, type)
    }
}