import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // What `suggestRuntimeModules` reports beyond the runtime image's defaults.
            modules("java.instrument", "jdk.unsupported")
            packageName = "Roto"
            packageVersion = project.version.toString()
            description = "Turns reference footage into pixel-art drafts for hand polish."
            vendor = "Roto"
            licenseFile.set(rootProject.file("LICENSE"))

            macOS {
                // A .dmg needs a major version of at least 1, so it takes its own while the project is 0.x.
                packageVersion = "1.0.0"
            }
            windows {
                menuGroup = "Roto"
                shortcut = true
                // Fixed, so a newer installer upgrades an installed Roto instead of sitting beside it.
                upgradeUuid = "6c1f2d3a-8b47-4e59-9a0c-3d5e7f1b2c84"
            }
            linux {
                packageName = "roto"
            }
        }
    }
}
