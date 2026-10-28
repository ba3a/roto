// The code in this file is a convention plugin - a Gradle mechanism for sharing reusable build logic.
// `buildSrc` is a Gradle-recognized directory and every plugin there will be easily available in the rest of the build.
package buildsrc.convention

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("jvm")
    id("io.gitlab.arturbosch.detekt")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED
        )
    }
}

// Static analysis. One shared rule set at config/detekt/detekt.yml, applied to every module. There is
// no baseline: the project starts clean and every line of code has to clear the bar.
detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

// detekt 1.23.x bundles the Kotlin 1.9 compiler, which cannot parse a newer JDK's version string
// (for example "25.0.2") when it derives a default JVM target. Pin it to the toolchain we actually
// target; without this every detekt task dies with IllegalArgumentException on a JDK 25 daemon.
tasks.withType<Detekt>().configureEach {
    jvmTarget = "21"

    reports {
        html.required = true
        xml.required = false
        txt.required = false
        sarif.required = false
        md.required = false
    }
}

tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget = "21"
}
