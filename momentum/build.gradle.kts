plugins {
    id("idea")
    id("net.fabricmc.fabric-loom") version "1.17.14"
}

val commonMainOutput = project(":common").extensions.getByType<SourceSetContainer>()["main"].output
val fabricMainOutput = project(":fabric").extensions.getByType<SourceSetContainer>()["main"].output

repositories {
    mavenLocal()
    maven { url = uri("https://maven.fabricmc.net") }
    maven { url = uri("https://maven.terraformersmc.com/") }
    maven { url = uri("https://maven.isxander.dev/releases") }
    mavenCentral()
}

val modernControlifySdk = configurations.create("modernControlifySdk")
val legacyControlifySdk = configurations.create("legacyControlifySdk")

dependencies {
    minecraft("com.mojang:minecraft:${rootProject.providers.gradleProperty("minecraft_version").get()}")

    implementation("net.fabricmc:fabric-loader:${rootProject.providers.gradleProperty("fabric_version").get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${rootProject.providers.gradleProperty("fabric_api_version").get()}")

    compileOnly(commonMainOutput)
    compileOnly(fabricMainOutput)
    runtimeOnly(fabricMainOutput)

    compileOnly("maven.modrinth:yacl:${rootProject.providers.gradleProperty("yacl_version").get()}")
    compileOnly("com.terraformersmc:modmenu:${rootProject.providers.gradleProperty("modmenu_version").get()}")
    add(modernControlifySdk.name, "maven.modrinth:controlify:${rootProject.providers.gradleProperty("controlify_modern_version").get()}") {
        isTransitive = false
    }
    add(legacyControlifySdk.name, "dev.isxander:controlify:${rootProject.providers.gradleProperty("controlify_version").get()}-fabric") {
        isTransitive = false
    }
    compileOnly(files(modernControlifySdk))
    testImplementation("net.fabricmc:fabric-loader-junit:${rootProject.providers.gradleProperty("fabric_version").get()}")
}

val legacyControlify = sourceSets.create("legacyControlify") {
    compileClasspath += sourceSets.main.get().output +
            (sourceSets.main.get().compileClasspath - modernControlifySdk) + legacyControlifySdk
}

// Opt-in integration probe; never part of either production artifact.
val controlifyIntegrationQa = sourceSets.create("controlifyIntegrationQa") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += output + compileClasspath
}

tasks.register<Jar>("controlifyIntegrationQaJar") {
    from(controlifyIntegrationQa.output)
    archiveFileName.set("momentum-controlify-integration-qa.jar")
    destinationDirectory.set(layout.buildDirectory.dir("qa"))
}

version = "${rootProject.extra["momentum_version"]}+${rootProject.providers.gradleProperty("minecraft_version").get()}-fabric"
group = "io.github.milkucha"

base {
    archivesName = "momentum"
}

loom {
    runs {
        named("client") {
            client()
            configName = "Momentum Client"
            ideConfigGenerated(true)
            runDir("run")
        }
        named("server") {
            server()
            configName = "Momentum Server"
            ideConfigGenerated(true)
            runDir("run")
        }
    }

    accessWidenerPath = project(":common").loom.accessWidenerPath
}

tasks {
    named<Jar>("jar") {
        from(legacyControlify.output)
    }
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    processResources {
        val loaderVersion = rootProject.providers.gradleProperty("fabric_version").get()
        val fabricApiVersion = rootProject.providers.gradleProperty("fabric_api_version").get()
        val automobilityVersion = rootProject.extra["momentum_automobility_min_version"].toString()
        inputs.property("version", version)
        inputs.property("fabric_loader_version", loaderVersion)
        inputs.property("fabric_api_version", fabricApiVersion)
        inputs.property("automobility_version", automobilityVersion)
        filesMatching("fabric.mod.json") {
            expand(mapOf(
                "version" to version,
                "fabric_loader_version" to loaderVersion,
                "fabric_api_version" to fabricApiVersion,
                "automobility_version" to automobilityVersion
            ))
        }
    }

    withType<Jar> {
        destinationDirectory = layout.buildDirectory.dir(
            if (name == "controlifyIntegrationQaJar") "qa" else "libs"
        )
    }

    withType<Test> {
        useJUnitPlatform()
    }
}
