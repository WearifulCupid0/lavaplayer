plugins {
  `java-library`
}

base {
  archivesName.set("lavaplayer")
}

dependencies {
  api(libs.slf4j)
  api(libs.annotations)
  api(libs.httpclient)

  api(project(":common"))

  api(libs.jackson.core)
  api(libs.jackson.databind)

  implementation(libs.commons.io)
  implementation(libs.jaad)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
}