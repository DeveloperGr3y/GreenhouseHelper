pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.7"
}

rootProject.name = "GreenhouseHelper"

// One codebase, one jar per Minecraft version. Per-version settings live in versions/<version>/gradle.properties.
stonecutter {
    create(rootProject) {
        versions("26.1", "26.2")
        vcsVersion = "26.2"
    }
}
