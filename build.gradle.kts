import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.attributes.Bundling
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.roborazzi) apply false
    kotlin("jvm") version "2.3.21" apply false
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
            pluginManager.apply("dev.detekt")
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")

            // ktlint otherwise lints Compose's generated `Res.kt` resource accessors under
            // build/generated — exclude all generated sources so only our code is checked.
            extensions.configure<KtlintExtension> {
                filter {
                    exclude { entry -> entry.file.path.contains("/generated/") }
                }
            }

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

// Single-file ktlint formatter used by the PostToolUse hook (.claude/hooks/post-edit-ktlint.sh).
// ktlint-gradle exposes only per-source-set format tasks, which reformat every file in the set;
// to touch exactly the edited file we drive the ktlint CLI on one path via `-PktlintFile=<path>`.
val ktlintCli: Configuration by configurations.creating {
    // ktlint-cli publishes two variants; pick the self-contained ("shadowed") fat jar.
    attributes {
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling::class.java, Bundling.SHADOWED))
    }
}

dependencies {
    ktlintCli(libs.ktlint.cli)
}

tasks.register<JavaExec>("ktlintFormatFile") {
    description = "Formats one Kotlin file with ktlint: ./gradlew ktlintFormatFile -PktlintFile=<path>"
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    // ktlint exits non-zero on non-autocorrectable issues; the hook must not fail on that.
    isIgnoreExitValue = true
    val target = (project.findProperty("ktlintFile") as String?)?.takeIf(String::isNotBlank)
    if (target != null) args("-F", target) else args("--help")
}

// task for easy run
tasks.register("runDesktop") {
    group = "_launch"
    dependsOn(":app:byCompose:desktop:run")
}
