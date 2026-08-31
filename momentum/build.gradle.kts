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

dependencies {
    minecraft("com.mojang:minecraft:${rootProject.providers.gradleProperty("minecraft_version").get()}")

    implementation("net.fabricmc:fabric-loader:${rootProject.providers.gradleProperty("fabric_version").get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${rootProject.providers.gradleProperty("fabric_api_version").get()}")

    compileOnly(commonMainOutput)
    compileOnly(fabricMainOutput)
    runtimeOnly(fabricMainOutput)

    compileOnly("maven.modrinth:yacl:${rootProject.providers.gradleProperty("yacl_version").get()}")
    compileOnly("com.terraformersmc:modmenu:${rootProject.providers.gradleProperty("modmenu_version").get()}")
    compileOnly("dev.isxander:controlify:${rootProject.providers.gradleProperty("controlify_version").get()}-fabric") {
        isTransitive = false
    }
    testImplementation("net.fabricmc:fabric-loader-junit:${rootProject.providers.gradleProperty("fabric_version").get()}")
}

version = "${rootProject.providers.gradleProperty("momentum_version").get()}+${rootProject.providers.gradleProperty("minecraft_version").get()}-fabric"
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
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    processResources {
        val loaderVersion = rootProject.providers.gradleProperty("fabric_version").get()
        val fabricApiVersion = rootProject.providers.gradleProperty("fabric_api_version").get()
        val automobilityVersion = rootProject.providers.gradleProperty("momentum_automobility_min_version").get()
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
        destinationDirectory = rootDir.resolve(project.name).resolve("build").resolve("libs")
    }

    withType<Test> {
        useJUnitPlatform()
    }
}
