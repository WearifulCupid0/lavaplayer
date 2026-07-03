plugins {
    `java-library`
    `maven-publish`
}

base {
    archivesName.set("lavaplayer-ext-redis-cache")
}

version = libs.versions.lavaplayer.ext.redis.cache.get()

dependencies {
    compileOnly(project(":main"))

    implementation(libs.lettuce)
    implementation(libs.slf4j)
    implementation(libs.commons.io)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}