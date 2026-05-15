import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvm()
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    sourceSets {

        val jvmAndroidMain by creating {
            dependsOn(commonMain.get())
            jvmMain.get().dependsOn(this)
            androidMain.get().dependsOn(this)
            dependencies {
                implementation(libs.kotlinx.serialization.json)
            }
        }

        commonMain.dependencies {
            //module with logic
            implementation(projects.shared)

            //decompose
            implementation(libs.decompose)
            implementation(libs.decompose.compose)
            implementation(libs.lifecycle.coroutines)

            //compose
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            implementation(projects.app.byCompose.theme)
            implementation(projects.app.byCompose.uikit)

            implementation(libs.kotlin.logging.common)
            implementation(libs.kotlinx.serialization.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(kotlin("test"))
        }

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlin.logging.android)
            implementation(libs.slf4j.api)
            implementation(libs.slf4j.simple)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlin.logging.jvm)
            implementation(libs.slf4j.api)
            implementation(libs.slf4j.simple)
            implementation(libs.apache.pdfbox)
            implementation(libs.jmdns)
        }
    }
}

android {
    namespace = "ru.kyamshanov.notepen"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}