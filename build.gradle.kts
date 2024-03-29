plugins {
  java
  `maven-publish`
}

group = "com.sedmelluq"

allprojects {
  group = rootProject.group

  repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://jitpack.io")
    maven(url = "https://m2.dv8tion.net/releases")
  }

  apply(plugin = "java")
  apply(plugin = "maven-publish")

  java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  publishing {
    repositories {
      maven {
        setUrl("s3://m2.dv8tion.net/releases")
        credentials(AwsCredentials::class) {
          accessKey = project.findProperty("sedmelluqMavenS3AccessKey")?.toString()
          secretKey = project.findProperty("sedmelluqMavenS3SecretKey")?.toString()
        }
      }
    }
  }
}