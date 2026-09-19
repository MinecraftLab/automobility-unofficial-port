@file:Suppress("UnstableApiUsage")

plugins {
    id("net.fabricmc.fabric-loom") version ("1.17.21")
}

dependencies {
    minecraft("com.mojang:minecraft:${rootProject.providers.gradleProperty("minecraft_version").get()}")

    compileOnly("io.github.llamalad7:mixinextras-common:0.3.5")
    annotationProcessor("io.github.llamalad7:mixinextras-common:0.3.5")

    compileOnly("net.fabricmc:sponge-mixin:0.15.3+mixin.0.8.7")
    implementation("net.fabricmc:fabric-loader:${rootProject.providers.gradleProperty("fabric_version").get()}")
    compileOnly("de.javagl:obj:0.4.0")
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
    mixin {
        useLegacyMixinAp = false
    }

    accessWidenerPath = file("src/main/resources/automobility.accesswidener")
}

tasks {
    withType<JavaCompile> {
        automobilityLiteClientExcludes.forEach { exclude(it) }
    }

    jar { enabled = false }
}
