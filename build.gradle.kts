plugins {
    // Gathers every module's KDoc into one site: `./gradlew dokkaGenerate` writes build/dokka/html.
    id("org.jetbrains.dokka")
}

dependencies {
    dokka(project(":roto-core"))
    dokka(project(":roto-cli"))
    dokka(project(":roto-app"))
}

allprojects {
    group = "com.roto"
    version = "0.1.0"
}
