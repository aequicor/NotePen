import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    explicitApi()
    applyDefaultHierarchyTemplate()
    jvm()
    androidLibrary {
        namespace = "io.aequicor.notepen.uikit.sandbox"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.app.byCompose.theme)
            implementation(projects.app.byCompose.uikit)
            implementation(projects.app.byCompose.common)
            implementation(projects.app.byCompose.blur)
            implementation(projects.shared)
            implementation(projects.drawing.api)

            implementation(libs.decompose)
            implementation(libs.decompose.compose)
            implementation(libs.lifecycle.coroutines)
            implementation(libs.mvikotlin)
            implementation(libs.mvikotlin.main)
            implementation(libs.mvikotlin.extensions.coroutines)
            implementation(libs.koin.core)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(compose.material3)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.components.ui.tooling.preview)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.activity.compose)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

tasks.register("runDesktop") {
    group = "_launch"
    description = "Runs the standalone UIKit sandbox desktop application."
    dependsOn(":app:byCompose:uikit-sandbox:desktop:run")
}

tasks.register("installAndroidDebug") {
    group = "_launch"
    description = "Installs the standalone UIKit sandbox Android debug application."
    dependsOn(":app:byCompose:uikit-sandbox:android:installDebug")
}
