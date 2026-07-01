plugins {
  `java-library`
  groovy
  `maven-publish`
}

val moduleName = "lavaplayer"
version = "1.3.99"

dependencies {
  api(project(":common"))
  implementation("com.github.walkyst:lavaplayer-natives-fork:1.0.1")
  implementation("com.github.walkyst.JAADec-fork:jaadec-ext-aac:0.1.3")
  implementation("org.mozilla:rhino-engine:1.7.14")

  api("org.apache.httpcomponents:httpclient:4.5.10")

  implementation("net.iharder:base64:2.3.9")
  implementation("org.jetbrains:annotations:20.1.0")

  implementation("org.slf4j:slf4j-api:2.0.18")
  implementation("commons-io:commons-io:2.16.1")
  implementation("org.jsoup:jsoup:1.22.2")
  api("com.fasterxml.jackson.core:jackson-core:2.22.0")
  api("com.fasterxml.jackson.core:jackson-databind:2.22.0")

  implementation("org.json:json:20240303")

  testImplementation("org.codehaus.groovy:groovy:2.5.5")
  testImplementation("org.spockframework:spock-core:1.2-groovy-2.5")
  testImplementation("ch.qos.logback:logback-classic:1.2.3")
  testImplementation("com.sedmelluq:lavaplayer-test-samples:1.3.11")
}

tasks.jar {
  exclude("natives")
}

val generatedResourcesDir = layout.buildDirectory.dir("generated/resources/version")

val generateVersion by tasks.registering {
  val versionFile = generatedResourcesDir.map {
    it.file("com/sedmelluq/discord/lavaplayer/tools/version.txt")
  }

  outputs.file(versionFile)

  doLast {
    val file = versionFile.get().asFile
    file.parentFile.mkdirs()
    file.writeText(version.toString())
  }
}

sourceSets {
  main {
    resources.srcDir(generatedResourcesDir)
  }
}

tasks.processResources {
  dependsOn(generateVersion)
}

tasks.register<JavaExec>("sourceManagerHealthCheck") {
  dependsOn(tasks.testClasses)

  group = "verification"
  description = "Checks which audio source managers are currently working."

  classpath = sourceSets["test"].runtimeClasspath
  mainClass.set("com.sedmelluq.discord.lavaplayer.source.SourceManagerHealthCheck")
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