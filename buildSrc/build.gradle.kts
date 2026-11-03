plugins {
    // The Kotlin DSL plugin provides a convenient way to develop convention plugins.
    // Convention plugins are located in `src/main/kotlin`, with the file extension `.gradle.kts`,
    // and are applied in the project's `build.gradle.kts` files as required.
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Add a dependency on the Kotlin Gradle plugin, so that convention plugins can apply it.
    implementation(libs.kotlinGradlePlugin)
    // ...and on the detekt plugin, so the convention plugin can apply static analysis everywhere.
    implementation(libs.detektGradlePlugin)
    // ...and on Dokka, so every module can publish its KDoc and the root can gather them.
    implementation(libs.dokkaGradlePlugin)
}
