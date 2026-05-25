import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    kotlin("jvm") version "2.1.0" apply false
}

subprojects {
    // Apply the linters only to real Kotlin modules — reacting to the Kotlin plugin skips
    // the intermediate container projects (`:app`, `:drawing`, …) that hold no source, and
    // guarantees ktlint sees the Kotlin source sets (it is applied after the Kotlin plugin).
    listOf(
        "org.jetbrains.kotlin.multiplatform",
        "org.jetbrains.kotlin.jvm",
    ).forEach { kotlinPluginId ->
        pluginManager.withPlugin(kotlinPluginId) {
            pluginManager.apply("io.gitlab.arturbosch.detekt")
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")

            // detekt's default `detekt` task only scans the JVM `src/main` layout, which does
            // not exist in KMP modules. Point it at every Kotlin source root instead.
            extensions.configure<DetektExtension> {
                buildUponDefaultConfig = true
                config.setFrom(rootProject.files("config/detekt/detekt.yml"))
                baseline = file("detekt-baseline.xml")
                source.setFrom(
                    "src/commonMain/kotlin",
                    "src/jvmMain/kotlin",
                    "src/androidMain/kotlin",
                    "src/desktopMain/kotlin",
                    "src/iosMain/kotlin",
                    "src/main/kotlin",
                    "src/main/java",
                )
            }
        }
    }
}

// task for easy run
tasks.register("runDesktop") {
    group = "_launch"
    dependsOn(":app:byCompose:desktop:run")
}
