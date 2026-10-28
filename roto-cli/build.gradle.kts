plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

dependencies {
    implementation(project(":roto-core"))
    testImplementation(kotlin("test"))
}

application {
    applicationName = "roto"
    mainClass = "com.roto.cli.MainKt"
}
