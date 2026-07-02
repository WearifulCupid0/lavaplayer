plugins {
  `java-library`
  `maven-publish`
}

base {
  archivesName.set("lavaplayer-stream-merger")
}

libs.versions.lavaplayer.stream.merger.get()

dependencies {
  implementation(project(":main"))
  implementation(libs.slf4j)
}

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      from(components["java"])
    }
  }
}