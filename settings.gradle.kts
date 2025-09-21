
rootProject.name = "godot-scala-template"

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        google()
    }

    resolutionStrategy.eachPlugin {
        if (requested.id.id == "com.utopia-rise.godot-kotlin-jvm") {
            useModule("com.utopia-rise:godot-gradle-plugin:${requested.version}")
        }
    }
}

plugins {
    // to automatically download the toolchain jdk if missing
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0" // https://plugins.gradle.org/plugin/org.gradle.toolchains.foojay-resolver-convention
}
