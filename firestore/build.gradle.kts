/*
 * Copyright (c) 2018 Otalia Studios. Author: Mattia Iavarone.
 */

import com.otaliastudios.tools.publisher.common.License
import com.otaliastudios.tools.publisher.common.Release

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("com.otaliastudios.tools.publisher")
}

android {
    setCompileSdkVersion(property("compileSdkVersion") as Int)
    defaultConfig {
        setMinSdkVersion(property("minSdkVersion") as Int)
        setTargetSdkVersion(property("targetSdkVersion") as Int)
        versionName = property("libVersion") as String
    }

    buildFeatures {
        dataBinding = true
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
        getByName("test").java.srcDirs("src/test/kotlin")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildTypes {
        getByName("release").consumerProguardFile("proguard-rules.pro")
    }

    kotlinOptions {
        // Until the explicitApi() works in the Kotlin block...
        // https://youtrack.jetbrains.com/issue/KT-37652
        freeCompilerArgs += listOf("-Xexplicit-api=strict")
    }
}

dependencies {
    api("com.google.firebase:firebase-firestore-ktx:21.6.0")
}

publisher {
    project.artifact = "firestore"
    project.description = property("libDescription") as String
    project.group = property("libGroup") as String
    project.url = property("githubUrl") as String
    project.vcsUrl = property("githubGit") as String
    project.addLicense(License.APACHE_2_0)
    release.setSources(Release.SOURCES_AUTO)
    release.setDocs(Release.DOCS_AUTO)
    bintray {
        auth.user = "BINTRAY_USER"
        auth.key = "BINTRAY_KEY"
        auth.repo = "BINTRAY_REPO"
    }
    directory {
        directory = "../build/maven"
    }
}