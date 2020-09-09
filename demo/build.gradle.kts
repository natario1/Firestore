/*
 * Copyright (c) 2018 Otalia Studios. Author: Mattia Iavarone.
 */

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
}

android {
    setCompileSdkVersion(rootProject.extra["compileSdkVersion"] as Int)

    defaultConfig {
        applicationId = "com.otalistudios.firestore.demo"
        setMinSdkVersion(21)
        setTargetSdkVersion(rootProject.extra["targetSdkVersion"] as Int)
        versionCode = 1
        versionName = "1.0"
    }

    sourceSets {
        getByName("main").java.srcDir("src/main/kotlin")
        getByName("test").java.srcDir("src/test/kotlin")
    }

    buildTypes {
        getByName("debug").isMinifyEnabled = false
        getByName("release").isMinifyEnabled = false
    }
}

dependencies {

    implementation(project(":firestore"))
    kapt(project(":compiler"))
    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("androidx.core:core-ktx:1.3.1")
    implementation("androidx.constraintlayout:constraintlayout:2.0.1")
}
