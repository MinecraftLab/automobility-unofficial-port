plugins {
    id("idea")
    id("net.fabricmc.fabric-loom") version "1.17.14"
}

repositories {
    mavenLocal()
    maven { url = uri("https://maven.fabricmc.net") }
    maven { url = uri("https://maven.quiltmc.org/repository/release/") }
    maven { url = uri("https://maven.terraformersmc.com/") }
    maven { url = uri("https://ueaj.dev/maven") }
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://maven.isxander.dev/releases") }
}

// Compile the two optional API families separately; neither SDK is bundled.
val modernControlifySdk = configurations.create("modernControlifySdk")
val legacyControlifySdk = configurations.create("legacyControlifySdk")

dependencies {
    minecraft("com.mojang:minecraft:${rootProject.providers.gradleProperty("minecraft_version").get()}")

    implementation("net.fabricmc:fabric-loader:${rootProject.providers.gradleProperty("fabric_version").get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${rootProject.providers.gradleProperty("fabric_api_version").get()}")
    testImplementation("net.fabricmc:fabric-loader-junit:${rootProject.providers.gradleProperty("fabric_version").get()}")

    implementation("de.javagl:obj:0.4.0")
    include("de.javagl:obj:0.4.0")

    // Controlify
    add(modernControlifySdk.name, "maven.modrinth:controlify:${rootProject.providers.gradleProperty("controlify_modern_version").get()}") {
        isTransitive = false
    }
    add(legacyControlifySdk.name, "dev.isxander:controlify:${rootProject.providers.gradleProperty("controlify_version").get()}-fabric") {
        isTransitive = false
    }
    compileOnly(files(modernControlifySdk))

    implementation(project.project(":common").sourceSets.getByName("main").output)
}

// Explicit opt-in fixture, deliberately separate from the production artifact.
val animalBoardingQa = sourceSets.create("animalBoardingQa") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += output + compileClasspath
}

val legacyControlify = sourceSets.create("legacyControlify") {
    compileClasspath += sourceSets.main.get().output +
            (sourceSets.main.get().compileClasspath - modernControlifySdk) + legacyControlifySdk
}

tasks.register<Jar>("animalBoardingQaJar") {
    dependsOn(tasks.named(animalBoardingQa.classesTaskName))
    from(animalBoardingQa.output)
    archiveFileName.set("automobility-animal-boarding-qa.jar")
    destinationDirectory.set(layout.buildDirectory.dir("qa"))
}

val automobilityLiteClientExcludes = listOf(
    "**/AutomobilityClient.java",
    "**/automobile/render/item/**",
    "**/block/model/**",
    "**/mixin/EntityRenderDispatcherMixin.java",
    "**/mixin/EntityRenderersMixin.java",
    "**/mixin/KeyMappingAccess.java",
    "**/mixin/MultiPlayerGameModeMixin.java",
    "**/mixin/SoundChannelAccess.java",
    "**/mixin/SoundEngineMixin.java",
)

sourceSets {
    main {
        java {
            automobilityLiteClientExcludes.forEach { exclude(it) }
        }
    }
}

loom {
    runs {
        named("client") {
            client()
            configName = "Client"
            ideConfigGenerated(true)
            runDir("run")
        }
        named("server") {
            server()
            configName = "Server"
            ideConfigGenerated(true)
            runDir("run")
        }
    }

    accessWidenerPath = project(":common").loom.accessWidenerPath
}

tasks {
    named<JavaCompile>("compileJava") {
        // Include common code in the compiled Fabric jar.
        source(project(":common").sourceSets.main.get().allSource)
        automobilityLiteClientExcludes.forEach { exclude(it) }
    }

    withType<Test> {
        useJUnitPlatform()
        doFirst {
            systemProperty("controlify.modern.jar", modernControlifySdk.singleFile.absolutePath)
            systemProperty("controlify.legacy.jar", legacyControlifySdk.singleFile.absolutePath)
        }
    }

    // put all artifacts in the right directory
    withType<Jar> {
        destinationDirectory = layout.buildDirectory.dir(
            if (name == "animalBoardingQaJar") "qa" else "libs"
        )
    }

    named<Jar>("jar") {
        from(legacyControlify.output)
        from(rootDir.resolve("THIRD_PARTY_LICENSES").resolve("obj-0.4.0-LICENSE.txt")) {
            into("THIRD_PARTY_LICENSES")
        }
    }

    javadoc { source(project(":common").sourceSets.main.get().allJava) }

    processResources {
        val modVersion = rootProject.providers.gradleProperty("mod_version").get()
        val loaderVersion = rootProject.providers.gradleProperty("fabric_version").get()
        val fabricApiVersion = rootProject.providers.gradleProperty("fabric_api_version").get()
        inputs.property("version", modVersion)
        inputs.property("fabric_loader_version", loaderVersion)
        inputs.property("fabric_api_version", fabricApiVersion)

        from(project(":common").sourceSets.main.get().resources)

        filesMatching("fabric.mod.json") {
            expand(mapOf(
                "version" to modVersion,
                "fabric_loader_version" to loaderVersion,
                "fabric_api_version" to fabricApiVersion
            ))
        }
    }

}
