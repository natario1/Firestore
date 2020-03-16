package com.otaliastudios.firestore.compiler

data class Property(
        val name: String,
        val type: String,
        val isNullable: Boolean,
        val isBindable: Boolean = false)