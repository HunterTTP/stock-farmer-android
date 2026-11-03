plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("app/src/**/*.kt", "core/src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.kts", "app/*.kts", "core/*.kts")
        ktlint(libs.versions.ktlint.get())
    }
}
