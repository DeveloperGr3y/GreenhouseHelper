plugins {
    id("dev.kikugie.stonecutter")
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT" apply false
    kotlin("jvm") version "2.4.10" apply false
    id("com.gradleup.shadow") version "9.6.0" apply false
}

// The version whose code is "live" (uncommented) in src/. Switch with the Stonecutter "Set active project" tasks.
stonecutter active "26.2"
