package com.otaliastudios.firestore.compiler

import kotlinx.metadata.ClassName
import kotlinx.metadata.Flags
import kotlinx.metadata.KmTypeVisitor
import kotlinx.metadata.KmVariance

class ReturnTypeVisitor(
        private val key: String,
        private val nullable: Boolean,
        private val bindable: Boolean,
        private val map: MutableMap<String, PropertyInfo>) : KmTypeVisitor() {

    private var isFirestoreMapOrFirestoreList: Boolean = false
    private var returnClassName: String = ""

    override fun visitClass(name: ClassName) {
        returnClassName = name.readable()
        isFirestoreMapOrFirestoreList = name.isFirestoreMapOrFirestoreList()
    }

    override fun visitArgument(flags: Flags, variance: KmVariance): KmTypeVisitor? {
        return if (!isFirestoreMapOrFirestoreList) null else object: KmTypeVisitor() {
            // This is either FirestoreList or FirestoreMap. This function will be called only once.
            // This also means that the class will not go through the Processor.
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