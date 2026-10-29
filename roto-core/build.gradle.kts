plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    // roto.json, profile.json and the publish manifest are all JSON; one serializer for every format.
    implementation(libs.kotlinxSerializationJson)
    testImplementation(kotlin("test"))
}
