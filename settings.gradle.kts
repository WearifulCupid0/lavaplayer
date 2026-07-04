@file:Suppress("UnstableApiUsage")

import org.gradle.api.initialization.dsl.VersionCatalogBuilder
import org.gradle.kotlin.dsl.plugin
import org.gradle.kotlin.dsl.version

rootProject.name = "lavaplayer"

// enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    ":common",
    ":main",
    ":source-module",
    ":stream-merger",
    ":test-samples",
    ":extensions:third-party-sources",
    ":extensions:format-xm",
    ":extensions:redis-cache",
    ":natives",
    ":natives-publish"
)

project(":extensions").name = "extensions-project"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://m2.dv8tion.net/releases")
        maven("https://jitpack.io")
    }

    versionCatalogs {
        create("libs") {
            version("project", "0.2.0")
            version("java", "11")

            versions()
            plugins()
            common()
            source()
            test()
            modules()
            others()
        }
    }
}

fun VersionCatalogBuilder.versions() {
    version("project", "0.1.0")
    version("java", "11")
    version("maven-publish-plugin", "0.32.0")

    version("slf4j", "1.7.25")
    version("commons-io", "2.6")
    version("annotations", "24.0.0")
    version("jackson", "2.15.2")
    version("httpclient", "4.5.14")
    version("jsoup", "1.12.1")
    version("json", "20240303")
    version("base64", "2.3.9")
    version("junit", "5.10.0")
    version("groovy", "4.0.13")

    version("logback", "1.1.8")
    version("ibxm-fork", "a75")
    version("jaad", "0.1.3")
    version("lettuce", "6.7.1.RELEASE")
}

fun VersionCatalogBuilder.plugins() {
    val vanniktech = version("vanniktech-maven-publish", "0.32.0")
    plugin("vanniktech-maven-publish", "com.vanniktech.maven.publish").versionRef(vanniktech)
    plugin("vanniktech-maven-publish-base", "com.vanniktech.maven.publish.base").versionRef(vanniktech)

    plugin("maven-publish", "com.vanniktech.maven.publish")
        .versionRef("maven-publish-plugin")

    plugin("maven-publish-base", "com.vanniktech.maven.publish.base")
        .versionRef("maven-publish-plugin")
}

fun VersionCatalogBuilder.common() {
    library("slf4j", "org.slf4j", "slf4j-api").versionRef("slf4j")
    library("commons-io", "commons-io", "commons-io").versionRef("commons-io")
    library("annotations", "org.jetbrains", "annotations").versionRef("annotations")

    library("jackson-core", "com.fasterxml.jackson.core", "jackson-core").versionRef("jackson")
    library("jackson-databind", "com.fasterxml.jackson.core", "jackson-databind").versionRef("jackson")

    library("httpclient", "org.apache.httpcomponents", "httpclient").versionRef("httpclient")
    library("jaad", "com.github.walkyst.JAADec-fork", "jaadec-ext-aac").versionRef("jaad")
}

fun VersionCatalogBuilder.source() {
    library("jsoup", "org.jsoup", "jsoup").versionRef("jsoup")
    library("json", "org.json", "json").versionRef("json")
    library("base64", "net.iharder", "base64").versionRef("base64")
}

fun VersionCatalogBuilder.test() {
    library("groovy", "org.apache.groovy", "groovy").versionRef("groovy")
    library("junit-bom", "org.junit", "junit-bom").versionRef("junit")
    library("junit-jupiter", "org.junit.jupiter", "junit-jupiter").withoutVersion()
}

fun VersionCatalogBuilder.modules() {
    version("lava-common", "1.1.2")
    version("lavaplayer-test-samples", "1.3.11")
    version("lavaplayer-stream-merger", "0.1.0")
    version("lavaplayer-ext-format-xm", "0.1.0")
    version("lavaplayer-ext-third-party-sources", "0.1.8")
    version("lavaplayer-ext-redis-cache", "0.1.0")
    version("lavaplayer-source", "0.1.1")
    version("lavaplayer-main", "0.1.1")
}

fun VersionCatalogBuilder.others() {
    library("logback-classic", "ch.qos.logback", "logback-classic").versionRef("logback")
    library("ibxm-fork", "com.github.walkyst", "ibxm-fork").versionRef("ibxm-fork")
    library("lettuce", "io.lettuce", "lettuce-core").versionRef("lettuce")
}

fun VersionCatalogBuilder.natives() {
    library("natives", "dev.arbjerg", "lavaplayer")
}