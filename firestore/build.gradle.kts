/*
 * Copyright (c) 2018 Otalia Studios. Author: Mattia Iavarone.
 */

import com.otaliastudios.tools.publisher.PublisherExtension.License
import com.otaliastudios.tools.publisher.PublisherExtension.Release

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("maven-publisher-bintray")
}

android {
    setCompileSdkVersion(property("compileSdkVersion") as Int)
    defaultConfig {
        setMinSdkVersion(property("minSdkVersion") as Int)
        setTargetSdkVersion(property("targetSdkVersion") as Int)
        versionName = property("libVersion") as String
    }

    dataBinding.isEnabled = true

    sourceSets {
        get("main").java.srcDirs("src/main/kotlin")
        get("test").java.srcDirs("src/test/kotlin")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildTypes {
        get("release").consumerProguardFile("proguard-rules.pro")
    }
}

dependencies {
    val kotlinVersion = property("kotlinVersion")
    api("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion")
    api("com.google.firebase:firebase-firestore:21.4.1")
    api("com.jakewharton.timber:timber:4.7.1")
}

publisher {
    auth.user = "BINTRAY_USER"
    auth.key = "BINTRAY_KEY"
    auth.repo = "BINTRAY_REPO"
    project.artifact = "firestore"
    project.description = property("libDescription") as String
    project.group = property("libGroup") as String
    project.url = property("githubUrl") as String
    project.vcsUrl = property("githubGit") as String
    project.addLicense(License.APACHE_2_0)
    release.setSources(Release.SOURCES_AUTO)
    release.setDocs(Release.DOCS_AUTO)
}