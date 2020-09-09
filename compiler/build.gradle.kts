/*
 * Copyright (c) 2018 Otalia Studios. Author: Mattia Iavarone.
 */

import com.otaliastudios.tools.publisher.common.License
import com.otaliastudios.tools.publisher.common.Release

plugins {
    id("kotlin")
    id("com.otaliastudios.tools.publisher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    api("com.squareup:kotlinpoet:1.5.0")
    api("com.squareup:kotlinpoet-metadata:1.5.0")
    api("com.squareup:kotlinpoet-metadata-specs:1.5.0")
}

publisher {
    project.artifact = "firestore-compiler"
    project.description = property("libDescription") as String
    project.group = property("libGroup") as String
    project.url = property("githubUrl") as String
    project.vcsUrl = property("githubGit") as String
    project.addLicense(License.APACHE_2_0)
    release.version = property("libVersion") as String
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
