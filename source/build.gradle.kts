plugins {
    `java-library`
    groovy
    `maven-publish`
}

group = "com.sedmelluq"

val moduleName = "lavaplayer-source-module"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(project(":main"))
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("sourceManagerHealthCheck") {
    dependsOn(tasks.testClasses)

    group = "verification"
    description = "Checks which audio source managers are currently working."

    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.sedmelluq.lavaplayer.source.SourceManagerHealthCheck")
}
