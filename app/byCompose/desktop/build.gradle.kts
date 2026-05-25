import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
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

    // JBR provides the WindowDecorations API used by setupJbrTitleBar (custom title
    // bar + native Windows snapping). Pinning the desktop toolchain to the JetBrains
    // vendor makes :run / :runDistributable and the jpackage/jlink bundled runtime use
    // JBR, so the custom title bar is active in dev and in shipped builds. Resolved from
    // a locally-installed JBR 21 — foojay cannot auto-provision the JetBrains vendor, so
    // a machine without one in an auto-detected dir must set
    // org.gradle.java.installations.paths in its user-level gradle.properties.
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.JETBRAINS)
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

        // Задачи Compose Desktop run / package* НЕ используют kotlin `jvmToolchain`
        // (он влияет только на компиляцию). По умолчанию они берут JDK, которым
        // запущен Gradle (current JVM — обычно Temurin). На не-JBR рантайме
        // JBR.isWindowDecorationsSupported() == false, поэтому setupJbrTitleBar
        // возвращает null и кастомный титлбар молча не активируется — и в :run, и
        // в собранном дистрибутиве (jpackage берёт рантайм из того же javaHome).
        // Поэтому явно указываем JBR. compilerFor (требует javac) выбирает
        // полноценный jbrsdk с jpackage/jlink/jmods, а не runtime-only jbr.
        javaHome = javaToolchains.compilerFor {
            languageVersion.set(JavaLanguageVersion.of(21))
            vendor.set(JvmVendorSpec.JETBRAINS)
        }.map { it.metadata.installationPath.asFile.absolutePath }.get()

        nativeDistributions {
            includeAllModules = false
            modules = arrayListOf(
                "java.base",
                "java.desktop",
                "java.logging",
                "java.sql",
                "jdk.crypto.ec"
            )
            // Windows .exe собирается НЕ через jpackage (TargetFormat.Exe), а из
            // app-image (createReleaseDistributable) внешним инсталлятором Inno Setup
            // — см. installer/windows/notepen.iss. Причина: jpackage не умеет
            // добавить финальный чекбокс «Запустить NotePen» и opt-in галочку ярлыка.
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Deb
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
                // Влияет только на иконку лаунчера NotePen.exe внутри app-image
                // (createReleaseDistributable). Установку (per-user без админа,
                // ярлыки, чекбоксы «Запустить»/«Ярлык», ассоциация .pdf) делает
                // Inno Setup — installer/windows/notepen.iss.
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

// Portable Windows-дистрибутив: тот же релизный app-image, что оборачивает в
// инсталлятор Inno Setup, плюс файл-маркер NotePen.portable в корне архива. Он
// переключает приложение на хранение данных в <папка_exe>/data (см. getAppDataDir),
// поэтому ZIP можно распаковать и запустить без установки — ничего не оставляя в
// системе. Имеет смысл только на Windows-хосте: createReleaseDistributable
// собирает app-image под ОС сборки.
val packageReleasePortableZip by tasks.registering(Zip::class) {
    group = "compose desktop"
    description = "Packages the release app-image into a portable (no-install) Windows ZIP."
    dependsOn("createReleaseDistributable")

    val appVersion = providers.gradleProperty("app.version").getOrElse("1.0.0")
    from(layout.buildDirectory.dir("compose/binaries/main-release/app/NotePen"))
    from(rootProject.file("installer/windows/NotePen.portable"))

    archiveFileName.set("NotePen-$appVersion-portable-windows-x64.zip")
    destinationDirectory.set(layout.buildDirectory.dir("portable"))
}
