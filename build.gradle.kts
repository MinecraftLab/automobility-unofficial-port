import java.util.Properties

plugins {
	id("java")
	id("net.fabricmc.fabric-loom") version "1.17.14" apply false
}

// Release bases are independent of upstream and identical across Minecraft lanes.
// Reject CLI/gradle.properties shadowing instead of silently building a split release.
val releaseVersions = Properties().apply {
    rootProject.file("release-versions.properties").inputStream().use { load(it) }
}
val releaseKeys = setOf("mod_version", "momentum_version", "momentum_automobility_min_version")
require(releaseVersions.stringPropertyNames() == releaseKeys) { "Unexpected release version keys" }
releaseKeys.forEach { key ->
    require(!providers.gradleProperty(key).isPresent) { "Release version shadowing is forbidden: $key" }
    val value = releaseVersions.getProperty(key)
    require(value.matches(Regex("""[1-9][0-9]*\.[0-9]+\.[0-9]+(?:-(?:dev|beta)\.[1-9][0-9]*)?"""))) {
        "Invalid independent release base: $key=$value"
    }
    extra[key] = value
}

allprojects {
	apply(plugin = "java")
	apply(plugin = "maven-publish")
}

subprojects {
	apply(plugin = "java-library")
	apply(plugin = "maven-publish")

	repositories {
		mavenCentral()
		mavenLocal()

		exclusiveContent {
			forRepository {
				maven {
					name = "Modrinth"
					url = uri("https://api.modrinth.com/maven")
				}
			}
			filter {
				includeGroup("maven.modrinth")
			}
		}

		exclusiveContent {
			forRepository {
				maven {
					name = "Parchment"
					url = uri("https://maven.parchmentmc.org")
				}
			}
			filter {
				includeGroup("org.parchmentmc.data")
			}
		}
	}

	java.toolchain.languageVersion = JavaLanguageVersion.of("25")

	tasks {
		withType<JavaCompile> {
			options.encoding = "UTF-8"
			options.release.set(25)
		}
		withType<GenerateModuleMetadata>().configureEach {
			enabled = false
		}

		jar {
			destinationDirectory = rootDir.resolve(project.name).resolve("build").resolve("libs")

			from(rootDir.resolve("LICENSE"))
			from(rootDir.resolve("NOTICE.md"))

			duplicatesStrategy = DuplicatesStrategy.EXCLUDE
		}
	}

	version = "${releaseVersions.getProperty("mod_version")}+${rootProject.providers.gradleProperty("minecraft_version").get()}-${project.name}"
	group = providers.gradleProperty("maven_group").get()

	base {
		archivesName = rootProject.providers.gradleProperty("archives_base_name").get()
	}

	dependencies {
		compileOnly("org.jetbrains:annotations:26.0.1")
		compileOnly("de.javagl:obj:0.4.0")
	}
}

tasks.jar {
	enabled = false
}
