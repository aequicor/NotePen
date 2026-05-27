import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.roborazzi)
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvm()
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.reflow.api)
            implementation(projects.drawing.api)
            implementation(projects.app.byCompose.uikit)
            implementation(libs.haze)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(libs.kotlin.logging.common)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose.desktop)
        }
        jvmMain.dependencies {
            implementation(libs.kotlin.logging.jvm)
            implementation(libs.slf4j.api)
            implementation(libs.slf4j.simple)
            implementation(libs.apache.pdfbox)
        }
        androidMain.dependencies {
            implementation(libs.kotlin.logging.android)
            implementation(libs.pdfbox.android)
        }
    }
}

android {
    namespace = "ru.kyamshanov.notepen.reflow.impl"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
