plugins {
    `java-library`
    groovy
}

base {
    archivesName.set("lavaplayer-source-module")
}

dependencies {
    api(project(":main"))

    api(libs.slf4j)
    api(libs.annotations)
    api(libs.httpclient)

    implementation(libs.jsoup)
    implementation(libs.json)
    implementation(libs.base64)

    implementation(libs.commons.io)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.groovy)
}

tasks.register<JavaExec>("sourceManagerHealthCheck") {
    dependsOn(tasks.testClasses)

    group = "verification"
    description = "Checks which audio source managers are currently working."

    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.sedmelluq.lavaplayer.source.SourceManagerHealthCheck")
}