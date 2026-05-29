import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    explicitApi()
    applyDefaultHierarchyTemplate()
    jvm()
    androidLibrary {
        namespace = "io.aequicor.notepen.library.api"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api`, not `implementation`: CanonicalBookId from :shared is part of
            // this module's public API (a property type on LibraryEntry /
            // OpenableDocument), so consumers must see it transitively.
            api(projects.shared)
            implementation(libs.kotlinx.coroutines.core)
            // LibraryConnection is @Serializable (persisted as JSON by
            // LibraryConnectionStore); the sealed-hierarchy serializer is generated here.
            implementation(libs.kotlinx.serialization.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            // LibraryConnection JSON round-trip / backward-compat test.
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
