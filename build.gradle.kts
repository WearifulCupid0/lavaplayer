import org.gradle.api.JavaVersion
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.external.javadoc.StandardJavadocDocletOptions

val libsCatalog = extensions
  .getByType<VersionCatalogsExtension>()
  .named("libs")

val projectVersion = libsCatalog
  .findVersion("project")
  .get()
  .requiredVersion

val javaVersion = libsCatalog
  .findVersion("java")
  .get()
  .requiredVersion

allprojects {
  group = "com.sedmelluq"
  version = projectVersion
}

subprojects {
  apply(plugin = "java-library")
  apply(plugin = "maven-publish")

  extensions.configure<JavaPluginExtension> {
    val version = JavaVersion.toVersion(javaVersion)

    sourceCompatibility = version
    targetCompatibility = version

    withSourcesJar()
    withJavadocJar()
  }

  tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
  }

  tasks.withType<Test>().configureEach {
    useJUnitPlatform()
  }

  tasks.withType<Javadoc>().configureEach {
    isFailOnError = false

    options.encoding = "UTF-8"

    val javadocOptions = options as StandardJavadocDocletOptions
    javadocOptions.addBooleanOption("Xdoclint:none", true)
    javadocOptions.addBooleanOption("quiet", true)
  }
}