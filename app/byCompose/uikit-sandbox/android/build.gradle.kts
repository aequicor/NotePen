import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

android {
    namespace = "ru.kyamshanov.notepen.uikitsandbox.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "ru.kyamshanov.notepen.uikitsandbox"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.compileSdk.get().toInt()
        versionCode = 1
        versionName = providers.gradleProperty("app.version").getOrElse("1.0.0")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":app:byCompose:uikit-sandbox"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.decompose)
    implementation(libs.decompose.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.components.ui.tooling.preview)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
