rootProject.name = "Automobility"

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases")

        mavenCentral()
        gradlePluginPortal()
    }
}

include("common", "fabric", "momentum")
