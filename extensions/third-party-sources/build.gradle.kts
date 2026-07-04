plugins {
    `java-library`
    `maven-publish`
}

base {
    archivesName.set("lavaplayer-ext-third-party-sources")
}

version = libs.versions.lavaplayer.ext.third.party.sources.get()

dependencies {
    api(project(":main"))

    implementation(libs.commons.io)
    implementation(libs.slf4j)
    implementation(libs.jsoup)
}

tasks.register<JavaExec>("sourceManagerHealthCheck") {
    dependsOn(tasks.testClasses)

    group = "verification"
    description = "Checks which audio source managers are currently working."

    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.sedmelluq.lavaplayer.extensions.thirdpartysources.ThirdPartySourceManagerHealthCheck")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}