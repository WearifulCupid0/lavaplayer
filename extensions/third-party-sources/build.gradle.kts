plugins {
    `java-library`
    `maven-publish`
}

val moduleName = "lavaplayer-ext-third-party-sources"
version = "0.1.0"

dependencies {
    compileOnly(project(":main"))
    implementation("org.slf4j:slf4j-api:1.7.25")
}

val sourcesJar by tasks.registering(Jar::class) {
  archiveClassifier.set("sources")
  from(sourceSets["main"].allSource)
}

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      from(components["java"])
      artifactId = moduleName
      artifact(sourcesJar)
    }
  }
}