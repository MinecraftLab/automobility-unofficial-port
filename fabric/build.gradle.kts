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
    // Optional settings integrations are compiled, never made mandatory or bundled.
    compileOnly("maven.modrinth:yacl:${rootProject.providers.gradleProperty("yacl_version").get()}")
    compileOnly("com.terraformersmc:modmenu:${rootProject.providers.gradleProperty("modmenu_version").get()}")

    // The guarded renderer uses these optional APIs only when exact compatible mods
    // are installed. SDKs are compile-only and are never bundled or runtime requirements.
    // Explicit files let an offline build reuse its verified immutable SDK inputs.
    val irisSdk = providers.gradleProperty("automobilityIrisSdk").orNull
    val sodiumSdk = providers.gradleProperty("automobilitySodiumSdk").orNull
    compileOnly(if (irisSdk != null) files(irisSdk) else "maven.modrinth:iris:1.11.4+mc26.2")
    compileOnly(if (sodiumSdk != null) files(sodiumSdk) else "maven.modrinth:sodium:0.9.2+mc26.2")


    implementation(project.project(":common").sourceSets.getByName("main").output)
}

// Explicit opt-in fixture, deliberately separate from the production artifact.
val animalBoardingQa = sourceSets.create("animalBoardingQa") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += output + compileClasspath
}

// Dedicated-server acceptance fixture; never packaged with the production mod.
val vehicleLightsQa = sourceSets.create("vehicleLightsQa") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += output + compileClasspath
}

tasks.register<Jar>("vehicleLightsQaJar") {
    dependsOn(tasks.named(vehicleLightsQa.classesTaskName))
    from(vehicleLightsQa.output)
    archiveFileName.set("automobility-vehicle-lights-qa.jar")
    destinationDirectory.set(layout.buildDirectory.dir("qa"))
}

// Explicit native UI/surface fixture, kept outside the production jar.
val vehicleNativeQa = sourceSets.create("vehicleNativeQa") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += output + compileClasspath
}

tasks.register<Jar>("vehicleNativeQaJar") {
    dependsOn(tasks.named(vehicleNativeQa.classesTaskName))
    from(vehicleNativeQa.output)
    archiveFileName.set("automobility-vehicle-native-qa.jar")
    destinationDirectory.set(layout.buildDirectory.dir("qa"))
}

val legacyControlify = sourceSets.create("legacyControlify") {
    java.srcDir(rootDir.resolve("momentum/src/legacyControlify/java"))
    compileClasspath += sourceSets.main.get().output +
            (sourceSets.main.get().compileClasspath - modernControlifySdk) + legacyControlifySdk
}

// Task-owned acceptance fixture; never part of the production Core jar.
val garagePodiumQa = sourceSets.create("garagePodiumQa") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += output + compileClasspath
}
tasks.register<Jar>("garagePodiumQaJar") {
    dependsOn(tasks.named(garagePodiumQa.classesTaskName))
    from(garagePodiumQa.output)
    archiveFileName.set("automobility-garage-podium-QA-ONLY.jar")
    destinationDirectory.set(layout.buildDirectory.dir("qa"))
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
            srcDir(rootDir.resolve("momentum/src/main/java"))
            automobilityLiteClientExcludes.forEach { exclude(it) }
        }
        // One combined guide retains the exact existing Core and Momentum rules.
        resources.exclude("assets/controlify/contextual/guide/in_game.json")
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
            if (name == "animalBoardingQaJar" || name == "vehicleLightsQaJar" || name == "vehicleNativeQaJar" || name == "garagePodiumQaJar") "qa" else "libs"
        )
    }

    named<Jar>("jar") {
        from(legacyControlify.output)
        from(rootDir.resolve("LICENSE-ASSETS.txt"))
        from(rootDir.resolve("LICENSE-CC-BY-4.0.txt"))
        from(rootDir.resolve("THIRD_PARTY_LICENSES").resolve("obj-0.4.0-LICENSE.txt")) {
            into("THIRD_PARTY_LICENSES")
        }
    }

    javadoc { source(project(":common").sourceSets.main.get().allJava) }

    processResources {
        val modVersion = project.version.toString()
        val loaderVersion = rootProject.providers.gradleProperty("fabric_version").get()
        val fabricApiVersion = rootProject.providers.gradleProperty("fabric_api_version").get()
        inputs.property("version", modVersion)
        inputs.property("fabric_loader_version", loaderVersion)
        inputs.property("fabric_api_version", fabricApiVersion)

        from(project(":common").sourceSets.main.get().resources)
        from(rootDir.resolve("momentum/src/main/resources")) {
            // Exactly one loader identity and one set of entrypoint registrations.
            exclude("fabric.mod.json")
            exclude("assets/controlify/contextual/guide/in_game.json")
        }
        from(projectDir.resolve("src/unifiedDriving/resources"))
        from(rootDir.resolve("THIRD_PARTY_LICENSES")) {
            include("Momentum-LICENSE.txt", "Momentum-NOTICE.md")
            into("THIRD_PARTY_LICENSES")
        }

        filesMatching("fabric.mod.json") {
            expand(mapOf(
                "version" to modVersion,
                "fabric_loader_version" to loaderVersion,
                "fabric_api_version" to fabricApiVersion
            ))
        }
    }

}
