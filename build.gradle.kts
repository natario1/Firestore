/*
 * Copyright (c) 2018 Otalia Studios. Author: Mattia Iavarone.
 */

buildscript {

    extra["libDescription"] = "The efficient wrapper for Firestore model data."
    extra["libVersion"] = "0.7.0"
    extra["libGroup"] = "com.otaliastudios"
    extra["githubUrl"] = "https://github.com/natario1/Firestore"
    extra["githubGit"] = "https://github.com/natario1/Firestore.git"

    extra["minSdkVersion"] = 16
    extra["compileSdkVersion"] = 29
    extra["targetSdkVersion"] = 29

    repositories {
        google()
        mavenCentral()
        jcenter()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:4.0.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.0")
        classpath("com.otaliastudios.tools:publisher:0.3.3")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        jcenter()
    }
}

tasks.register("clean", Delete::class) {
    delete(buildDir)
}
