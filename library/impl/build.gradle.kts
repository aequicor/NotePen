import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvm()
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    androidLibrary {
        namespace = "io.aequicor.notepen.library.impl"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.library.api)
            implementation(projects.sync)
            implementation(projects.shared)

            implementation(libs.kotlin.logging)
            implementation(libs.kotlinx.coroutines.core)

            // Google Drive cloud backend: Ktor (engine injected by the DI layer) against the Drive
            // v3 REST API + its OAuth device flow, with JSON response parsing.
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
