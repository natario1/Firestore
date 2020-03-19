/*
 * Copyright (c) 2018 Otalia Studios. Author: Mattia Iavarone.
 */

import com.otaliastudios.tools.publisher.PublisherExtension.License
import com.otaliastudios.tools.publisher.PublisherExtension.Release

plugins {
    id("kotlin")
    id("maven-publisher-bintray")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    val kotlinVersion = property("kotlinVersion") as String
    api("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion")
    api("com.squareup:kotlinpoet:1.5.0")
    api("com.squareup:kotlinpoet-metadata:1.5.0")
    api("com.squareup:kotlinpoet-metadata-specs:1.5.0")
}

publisher {
    auth.user = "BINTRAY_USER"
    auth.key = "BINTRAY_KEY"
    auth.repo = "BINTRAY_REPO"
    project.artifact = "firestore-compiler"
    project.description = property("libDescription") as String
    project.group = property("libGroup") as String
    project.url = property("githubUrl") as String
    project.vcsUrl = property("githubGit") as String
    project.addLicense(License.APACHE_2_0)
    release.version = property("libVersion") as String
    release.setSources(Release.SOURCES_AUTO)
    release.setDocs(Release.DOCS_AUTO)
}
