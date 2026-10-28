plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":roto-core"))
    implementation(compose.desktop.currentOs)
    implementation(libs.composeMaterial3)
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.roto.app.MainKt"
    }
}
