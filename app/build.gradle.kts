plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/** libGDX ships its native libraries as classifier jars; they are unpacked into the folders Android loads them from. */
val natives: Configuration by configurations.creating
val nativeAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")

android {
    namespace = "com.hunter.stockfarmer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hunter.stockfarmer"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += nativeAbis }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }

    sourceSets.getByName("main").jniLibs.directories.add(
        layout.buildDirectory
            .dir("natives")
            .get()
            .asFile.path,
    )
}

kotlin {
    jvmToolchain(25)
}

val unpackNatives =
    nativeAbis.map { abi ->
        tasks.register<Copy>("unpackNatives_$abi") {
            from({ natives.files.filter { it.name.contains("natives-$abi") }.map { zipTree(it) } })
            include("*.so")
            into(layout.buildDirectory.dir("natives/$abi"))
        }
    }

tasks.named("preBuild") { dependsOn(unpackNatives) }

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.gdx)
    implementation(libs.gdx.backend.android)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    nativeAbis.forEach { natives("com.badlogicgames.gdx:gdx-platform:${libs.versions.gdx.get()}:natives-$it") }
}
