/*
 * Copyright (c) 2018 Otalia Studios. Author: Mattia Iavarone.
 */

import com.jfrog.bintray.gradle.BintrayExtension
import org.jetbrains.dokka.gradle.DokkaTask
import java.util.Properties
import java.util.Date
import java.io.FileInputStream

plugins {
    id("kotlin")
    id("maven-publish")
    id("org.jetbrains.dokka")
    id("com.jfrog.bintray")
}

val PUBLICATION = "bintrayPub"
val libName: String by rootProject.ext
val libDescription: String by rootProject.ext
val libVersion: String by rootProject.ext
val libGroup: String by rootProject.ext
val libArtifactId = "firestore-compiler"
val githubUrl: String by rootProject.ext
val githubGit: String by rootProject.ext
val libLicenseName: String by rootProject.ext
val libLicenseUrl: String by rootProject.ext

version = libVersion
group = libGroup
base.archivesBaseName = libArtifactId
java.sourceCompatibility = JavaVersion.VERSION_1_8
java.targetCompatibility = JavaVersion.VERSION_1_8

dependencies {
    val kotlinVersion: String by rootProject.ext
    val kotlinPoetVersion: String by rootProject.ext
    api("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion")
    api("com.squareup:kotlinpoet:$kotlinPoetVersion")
    api("com.squareup:kotlinpoet-metadata:$kotlinPoetVersion")
    api("com.squareup:kotlinpoet-metadata-specs:$kotlinPoetVersion")
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

val dokkaJar by tasks.registering(Jar::class) {
    val dokka = tasks.getByName<DokkaTask>("dokka")
    dependsOn(dokka)
    archiveClassifier.set("javadoc")
    from(dokka.outputDirectory)
}

publishing {
    publications {
        register(PUBLICATION, MavenPublication::class) {
            from(components["java"])
            artifact(sourcesJar.get())
            artifact(dokkaJar.get())
            groupId = libGroup
            artifactId = libArtifactId
            version = libVersion
            // Add extra data.
            pom.withXml {
                val root = asNode()
                root.appendNode("name", libName)
                root.appendNode("description", libDescription)
                root.appendNode("url", githubUrl)
                // TODO licenses, developers, scm, ...
            }
        }
    }
}

var bintrayUser: String? = null
var bintrayKey: String? = null
if (System.getenv("TRAVIS") == "true") {
    bintrayUser = System.getenv("BINTRAY_USER") ?: null
    bintrayKey = System.getenv("BINTRAY_KEY") ?: null
} else {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        val props = Properties()
        val stream = FileInputStream(rootProject.file("local.properties"))
        props.load(stream)
        bintrayUser = props["bintray.user"] as String
        bintrayKey = props["bintray.key"] as String
    }
}

bintray {
    // https://github.com/bintray/gradle-bintray-plugin
    setPublications(PUBLICATION)
    user = bintrayUser ?: ""
    key = bintrayKey ?: ""
    override = true
    publish = true
    pkg(delegateClosureOf<BintrayExtension.PackageConfig> {
        repo = "android"
        name = libName
        setLicenses(libLicenseName)
        vcsUrl = githubGit
        desc = libDescription
        version(delegateClosureOf<BintrayExtension.VersionConfig> {
            name = libVersion
            desc = "$libName v$libVersion"
            released = Date().toString()
            vcsTag = "v$libVersion"
        })
    })
}