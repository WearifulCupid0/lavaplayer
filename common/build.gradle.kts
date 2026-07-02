plugins {
  `java-library`
  `maven-publish`
}

base {
  archivesName.set("lava-common")
}

version = libs.versions.lava.common.get()

dependencies {
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