plugins {
    `java-library`
    `maven-publish`
}

base {
    archivesName.set("lavaplayer-ext-third-party-sources")
}

libs.versions.lavaplayer.ext.third.party.sources.get()

dependencies {
    compileOnly(project(":main"))

    implementation(libs.commons.io)
    implementation(libs.slf4j)
    implementation(libs.jsoup)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}