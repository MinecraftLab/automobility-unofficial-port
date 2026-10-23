rootProject.name = "Automobility"

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases")

        mavenCentral()
        gradlePluginPortal()
    }
}

// Momentum remains an internal source/test module; only :fabric distributes a mod.
include("common", "fabric", "momentum")
