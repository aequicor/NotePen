plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("NotePenSyncDatabase") {
            packageName.set("ru.kyamshanov.notepen.sync.db")
            // No migrations yet — disable the verify task that boots an
            // in-process SQLite from the Gradle daemon (its native library
            // doesn't load reliably across JVMs on Windows).
            verifyMigrations.set(false)
            verifyDefinitions.set(false)
        }
    }
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvm()
    // androidTarget Java/Kotlin level is configured via android { compileOptions }
    // below — SQLDelight 2.x's buildscript pin downgrades the KGP enough that
    // the new `compilerOptions { jvmTarget }` DSL stops compiling in this file.
    @Suppress("DEPRECATION")
    androidTarget {
        compilations.all {
            kotlinOptions { jvmTarget = "11" }
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Domain models live here (StrokeDelta -> DrawingPath) — read-only dependency.
            implementation(projects.shared)
            implementation(projects.drawing.api)

            implementation(libs.kotlin.logging.common)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)

            // SQLDelight: persistent offline queue for stroke deltas.
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.kotlin.logging.android)
            implementation(libs.slf4j.api)
            implementation(libs.slf4j.simple)
            implementation(libs.ktor.client.cio)
            implementation(libs.sqldelight.android.driver)
        }
        jvmMain.dependencies {
            implementation(libs.kotlin.logging.jvm)
            implementation(libs.slf4j.api)
            implementation(libs.slf4j.simple)
            implementation(libs.ktor.client.cio)

            // Host-side WebSocket server lives in :sync jvmMain.
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.websockets)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

android {
    namespace = "ru.kyamshanov.notepen.sync"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

// SQLDelight 2.x runs an in-process SQLite verifier against every schema on
// every `check` — and the bundled sqlite-jdbc fails to load its native library
// inside the Gradle daemon on this Windows JVM (`NativeDB._open_utf8`
// UnsatisfiedLinkError). We have no migrations yet, so disabling the verify
// task is safe; revisit when the first .sqm migration lands.
tasks.matching { it.name.startsWith("verify") && it.name.contains("NotePenSyncDatabase") }
    .configureEach { enabled = false }
