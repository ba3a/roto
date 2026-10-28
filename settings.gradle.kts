pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        // Compose Desktop pulls a few androidx artifacts that are published only to Google's repository.
        google()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":roto-core")
include(":roto-cli")
include(":roto-app")

rootProject.name = "roto"
