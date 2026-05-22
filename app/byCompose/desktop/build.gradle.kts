import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

/**
 * Удаляет из `sqlite-jdbc` нативные библиотеки всех платформ, кроме целевой.
 *
 * Jar тянет ~23 натива (Linux/Windows/FreeBSD/Mac × все архитектуры, ~23 МБ),
 * хотя в дистрибутиве нужна ровно одна либа под текущую ОС/архитектуру.
 * В CI каждая платформа собирается на своём раннере, поэтому `os.name`/`os.arch`
 * сборочной машины совпадают с целью. Прочие jar проходят без изменений.
 */
@CacheableTransform
abstract class StripForeignSqliteNatives : TransformAction<TransformParameters.None> {
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asFile
        if (!input.name.startsWith("sqlite-jdbc")) {
            outputs.file(input)
            return
        }
        val keepDir = currentPlatformNativeDir()
        val output = outputs.file(input.name)
        ZipFile(input).use { zip ->
            ZipOutputStream(output.outputStream().buffered()).use { out ->
                for (entry in zip.entries()) {
                    val name = entry.name
                    val foreignNative = name.startsWith(SQLITE_NATIVE_ROOT) && !name.startsWith(keepDir)
                    if (foreignNative) continue
                    out.putNextEntry(ZipEntry(name))
                    if (!entry.isDirectory) zip.getInputStream(entry).use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
        }
    }

    private fun currentPlatformNativeDir(): String {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val osDir = when {
            os.contains("mac") || os.contains("darwin") -> "Mac"
            os.contains("win") -> "Windows"
            else -> "Linux"
        }
        val archDir = when (arch) {
            "aarch64", "arm64" -> "aarch64"
            "amd64", "x86_64", "x64" -> "x86_64"
            "x86", "i386", "i686" -> "x86"
            else -> arch
        }
        return "$SQLITE_NATIVE_ROOT$osDir/$archDir/"
    }

    private companion object {
        const val SQLITE_NATIVE_ROOT = "org/sqlite/native/"
    }
}

private val sqliteStripped = Attribute.of("sqlite.natives.stripped", Boolean::class.javaObjectType)

dependencies {
    attributesSchema { attribute(sqliteStripped) }
    artifactTypes.getByName("jar") { attributes.attribute(sqliteStripped, false) }
    registerTransform(StripForeignSqliteNatives::class) {
        from.attribute(sqliteStripped, false).attribute(ARTIFACT_TYPE_ATTRIBUTE, "jar")
        to.attribute(sqliteStripped, true).attribute(ARTIFACT_TYPE_ATTRIBUTE, "jar")
    }
}

configurations.configureEach {
    if (name == "desktopRuntimeClasspath") {
        attributes.attribute(sqliteStripped, true)
    }
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

            // Регистрирует NotePen как обработчик .pdf, чтобы его можно было
            // выбрать приложением по умолчанию и открывать PDF «через него».
            // macOS: пишется в CFBundleDocumentTypes (Info.plist); путь к файлу
            // приходит как Apple Event "odoc" → java.awt.Desktop OpenFileHandler.
            // Windows: jpackage прокидывает путь как аргумент main().
            fileAssociation(
                mimeType = "application/pdf",
                extension = "pdf",
                description = "PDF Document",
            )

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
        )

        // -Xdock:icon is a macOS-only launcher option; passing it on Windows/Linux
        // aborts JVM startup ("Unrecognized option"). Gate on the host OS — only a
        // macOS host has a Dock and packages the .app anyway.
        // Registers the Dock icon at the OS level for the entire process lifetime;
        // Taskbar.setIconImage() only works while AWT is alive, this flag persists through shutdown.
        val hostOs = System.getProperty("os.name").lowercase()
        if (hostOs.contains("mac") || hostOs.contains("darwin")) {
            jvmArgs += "-Xdock:icon=${project.layout.projectDirectory.file("icons/app_icon.icns").asFile.absolutePath}"
        }
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
