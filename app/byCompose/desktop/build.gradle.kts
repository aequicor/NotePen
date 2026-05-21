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
            packageVersion = "1.0.0"
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

        jvmArgs += listOf("-Dorg.slf4j.simpleLogger.defaultLogLevel=DEBUG")
    }
}
