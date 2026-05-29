import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
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
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
