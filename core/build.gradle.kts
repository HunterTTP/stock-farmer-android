plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform { excludeTags("benchmark") }
}

// Timings of the costly rules on a late farm; see EngineBenchmark. Never part of `test` or `check`.
tasks.register<Test>("engineBenchmark") {
    group = "verification"
    description = "Times the costly rules on a fixed late farm, on this JVM."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("benchmark") }
    testLogging.showStandardStreams = true
    outputs.upToDateWhen { false }
}
