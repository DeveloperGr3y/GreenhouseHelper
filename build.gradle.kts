import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// This script runs once per Minecraft version (versions/26.1, versions/26.2).
plugins {
    id("net.fabricmc.fabric-loom")
    kotlin("jvm")
    id("com.gradleup.shadow")
}

val mcVersion = stonecutter.current.version
version = "${property("mod_version")}+mc$mcVersion"
group = "io.github.developergr3y"
base.archivesName.set("GreenhouseHelper")

repositories {
    maven("https://maven.notenoughupdates.org/releases") {
        content { includeGroupAndSubgroups("org.notenoughupdates") }
    }
    maven("https://repo.hypixel.net/repository/Hypixel") {
        content { includeGroup("net.hypixel") }
    }
    maven("https://api.modrinth.com/maven") {
        content { includeGroup("maven.modrinth") }
    }
}

// Libraries bundled (and renamed) inside our jar so they can't clash with other mods' copies.
val shadowImpl: Configuration by configurations.creating
configurations.implementation.get().extendsFrom(shadowImpl)

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")

    // Hypixel Mod API: only its API, for Hypixel's location packet. Not bundled; used only if its mod is installed.
    compileOnly("net.hypixel:mod-api:1.0.2")

    // Sodium: only to compile the hook that hides crops in its chunk builder. Not bundled; used only if it's installed.
    compileOnly("maven.modrinth:sodium:mc26.1.2-0.9.2-fabric")

    // Kotlin runtime, nested in our jar so players don't need to install it separately.
    val kotlinRuntime = "net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}"
    implementation(kotlinRuntime)
    include(kotlinRuntime)

    shadowImpl("org.notenoughupdates.moulconfig:modern-$mcVersion:${property("moulconfig_version")}") {
        exclude("org.jetbrains.kotlin")
        exclude("org.jetbrains.kotlinx")
    }
}

loom {
    accessWidenerPath = rootProject.file("src/main/resources/greenhousehelper.classtweaker")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget("25"))
    }
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "minecraft_range" to project.property("minecraft_range"),
        "loader_version" to project.property("loader_version"),
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}

tasks.shadowJar {
    configurations = listOf(shadowImpl)
    archiveClassifier.set("")
    destinationDirectory.set(rootProject.layout.buildDirectory.dir("libs"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/versions/**", "META-INF/*.kotlin_module", "moulconfig.accesswidener")
    mergeServiceFiles()
    relocate("io.github.notenoughupdates.moulconfig", "io.github.developergr3y.greenhousehelper.deps.moulconfig")
}
// Loom only nests `include`d jars into the plain jar task; nest them into the final jar too.
loom.nestJars(tasks.shadowJar, configurations.named("include"))

tasks.jar {
    archiveClassifier.set("nodeps")
    destinationDirectory.set(layout.buildDirectory.dir("badjars"))
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
