import org.jetbrains.compose.desktop.application.dsl.TargetFormat


plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm("desktop") {
        withJava()
    }

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.components.resources)
        }

        desktopMain.dependencies {
            implementation(projects.sync)
            implementation(projects.qrConnect)
            implementation(projects.shared)
            implementation(projects.drawing.api)
            implementation(projects.app.byCompose.common)
            implementation(projects.rendering.api)
            implementation(projects.rendering.impl)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.jna)
            implementation(libs.jna.platform)
            compileOnly(libs.jbr.api)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.serialization.kotlinx.json)

            //decompose
            implementation(libs.decompose)
            implementation(libs.decompose.compose)

            //compose
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            //   implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }
    }
}

compose.desktop {
    application {

        mainClass = "MainKt"

        nativeDistributions {
            includeAllModules = false
            modules = arrayListOf(
                "java.base",
                "java.desktop",
                "java.logging",
                "java.sql",
                "jdk.crypto.ec"
            )
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Deb,
                TargetFormat.Exe
            )
            packageName = "NotePen"
            packageVersion = providers.gradleProperty("app.version").getOrElse("1.0.0")
            description = "NotePen"
            copyright = "© 2025 KYamshanov. All rights reserved."
            vendor = "KYamshanov"

            windows {
                menu = true
                iconFile.set(project.file("icons/app_icon.ico"))
            }
            macOS {
                bundleID = "ru.kyamshanov.notepen"
                iconFile.set(project.file("icons/app_icon.icns"))
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSLocalNetworkUsageDescription</key>
                        <string>NotePen uses the local network to sync notes with your other devices.</string>
                        <key>NSBonjourServices</key>
                        <array>
                            <string>_notepen._tcp</string>
                        </array>
                    """.trimIndent()
                }
            }
            appResourcesRootDir.set(project.layout.projectDirectory.dir("assets"))
            jvmArgs += "-splash:app/resources/splash_logo.png"
        }
        buildTypes.release.proguard {
            obfuscate.set(true)
            isEnabled.set(true)
            configurationFiles.from("proguard-rules.pro")
        }

        jvmArgs += listOf(
            "-Dorg.slf4j.simpleLogger.defaultLogLevel=DEBUG",
            // In dev (:run), compose.application.resources.dir may not be set,
            // so JNA won't find dylibs in assets/ automatically. Point it there
            // explicitly; in packaged builds CocoaTabletInputController.addComposeResourcesToJnaPath()
            // does the same at runtime.
            "-Djna.library.path=${project.layout.projectDirectory.file("assets").asFile.absolutePath}",
            // macOS: register the Dock icon at the OS level for the entire process lifetime.
            // Taskbar.setIconImage() only works while AWT is alive; this flag persists through shutdown.
            "-Xdock:icon=${project.layout.projectDirectory.file("icons/app_icon.icns").asFile.absolutePath}",
        )
    }
}

// runDistributable launches the binary as a child of the Gradle/shell process.
// macOS then associates the Dock entry with the parent (Terminal/IntelliJ), and
// their icon flashes briefly when the AWT window closes before JVM exits.
// This task uses 'open -a' so macOS registers the bundle via NSWorkspace/Launch
// Services — the icon from CFBundleIconFile stays correct throughout shutdown.
tasks.register<Exec>("openApp") {
    dependsOn("createDistributable")
    commandLine(
        "open",
        layout.buildDirectory.file("compose/binaries/main/app/NotePen.app").get().asFile.absolutePath,
    )
}
