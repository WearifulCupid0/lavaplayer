plugins {
  `java-library`
  `maven-publish`
}

base {
  archivesName.set("lavaplayer-ext-format-xm")
}

libs.versions.lavaplayer.ext.format.xm.get()

dependencies {
  compileOnly(project(":main"))

  implementation(libs.ibxm.fork)
  implementation(libs.slf4j)
}

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      from(components["java"])
    }
  }
}