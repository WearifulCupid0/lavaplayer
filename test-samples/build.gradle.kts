plugins {
  java
  `maven-publish`
}

base {
  archivesName.set("lavaplayer-test-samples")
}

libs.versions.lavaplayer.test.samples.get()

// Sample files are not in repository, but must be present in src/main/resources during publish.
// Use previous samples dependency JAR to obtain them.

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      from(components["java"])
    }
  }
}