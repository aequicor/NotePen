import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvm()
    androidLibrary {
        namespace = "io.aequicor.notepen.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.decompose)
            implementation(libs.lifecycle.coroutines)
            implementation(libs.kotlin.logging)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            // AppSettings JSON round-trip / backward-compat test.
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.kotlin.logging)
            implementation(libs.slf4j.api)
            implementation(libs.slf4j.simple)
            implementation(libs.ktor.client.cio)
        }
        jvmMain.dependencies {
            implementation(libs.kotlin.logging)
            implementation(libs.slf4j.api)
            implementation(libs.slf4j.simple)
            implementation(libs.ktor.client.cio)
        }
    }
}
