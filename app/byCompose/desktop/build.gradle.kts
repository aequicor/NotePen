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
 * Подрезает сторонние jar для desktop-дистрибутива перед упаковкой и ProGuard.
 *
 * `sqlite-jdbc`: удаляет нативные либы всех платформ, кроме целевой. Jar тянет ~23
 * натива (Linux/Windows/FreeBSD/Mac × все архитектуры, ~23 МБ), хотя в дистрибутиве
 * нужна ровно одна под текущую ОС/архитектуру. В CI каждая платформа собирается на
 * своём раннере, поэтому `os.name`/`os.arch` сборочной машины совпадают с целью.
 *
 * `kotlin-logging-jvm`: удаляет опциональный logback-адаптер
 * (`io/github/oshai/kotlinlogging/logback/`). Он наследует отсутствующий `ch.qos.logback.*`
 * (логирование идёт через slf4j-simple) и роняет фазу оптимизации ProGuard с
 * `IncompleteClassHierarchyException` на суперклассе `LogbackLogEvent`. На рантайме адаптер
 * не используется (ветка logback в `KLoggerFactory` включается лишь системным свойством и
 * грузится лениво), поэтому удаление безопасно и держит оптимизацию ProGuard включённой.
 *
 * Оба пруна в одном трансформе под одной attribute намеренно: вторая кастомная attribute
 * со своим трансформом на artifactType `jar` ломает регистрацию Kotlin-таргета в KGP 2.1.0.
 *
 * Прочие jar проходят без изменений.
 */
@CacheableTransform
abstract class PruneDesktopRuntimeJars : TransformAction<TransformParameters.None> {
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asFile
        val drop: (String) -> Boolean
        if (input.name.startsWith("sqlite-jdbc")) {
            val keepDir = currentPlatformNativeDir()
            drop = { it.startsWith(SQLITE_NATIVE_ROOT) && !it.startsWith(keepDir) }
        } else if (input.name.startsWith("kotlin-logging-jvm")) {
            drop = { it.startsWith(LOGBACK_ADAPTER_ROOT) }
        } else {
            outputs.file(input)
            return
        }
        val output = outputs.file(input.name)
        ZipFile(input).use { zip ->
            ZipOutputStream(output.outputStream().buffered()).use { out ->
                for (entry in zip.entries()) {
                    if (drop(entry.name)) continue
                    out.putNextEntry(ZipEntry(entry.name))
                    if (!entry.isDirectory) zip.getInputStream(entry).use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
        }
    }

    private fun currentPlatformNativeDir(): String {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val osDir =
            when {
                os.contains("mac") || os.contains("darwin") -> "Mac"
                os.contains("win") -> "Windows"
                else -> "Linux"
            }
        val archDir =
            when (arch) {
                "aarch64", "arm64" -> "aarch64"
                "amd64", "x86_64", "x64" -> "x86_64"
                "x86", "i386", "i686" -> "x86"
                else -> arch
            }
        return "$SQLITE_NATIVE_ROOT$osDir/$archDir/"
    }

    private companion object {
        const val SQLITE_NATIVE_ROOT = "org/sqlite/native/"
        const val LOGBACK_ADAPTER_ROOT = "io/github/oshai/kotlinlogging/logback/"
    }
}

private val desktopJarsPruned = Attribute.of("desktop.jars.pruned", Boolean::class.javaObjectType)

dependencies {
    attributesSchema { attribute(desktopJarsPruned) }
    artifactTypes.getByName("jar") { attributes.attribute(desktopJarsPruned, false) }
    registerTransform(PruneDesktopRuntimeJars::class) {
        from.attribute(desktopJarsPruned, false).attribute(ARTIFACT_TYPE_ATTRIBUTE, "jar")
        to.attribute(desktopJarsPruned, true).attribute(ARTIFACT_TYPE_ATTRIBUTE, "jar")
    }
}

configurations.configureEach {
    if (name == "desktopRuntimeClasspath") {
        attributes.attribute(desktopJarsPruned, true)
    }
}

kotlin {
    jvm("desktop")

    // JBR provides the WindowDecorations API used by setupJbrTitleBar (custom title
    // bar + native Windows snapping). Pinning the desktop toolchain to the JetBrains
    // vendor makes :run / :runDistributable and the jpackage/jlink bundled runtime use
    // JBR, so the custom title bar is active in dev and in shipped builds. Resolved from
    // a locally-installed JBR 25 — foojay cannot auto-provision the JetBrains vendor, so
    // a machine without one in an auto-detected dir must set
    // org.gradle.java.installations.paths in its user-level gradle.properties.
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.JETBRAINS)
    }

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(libs.compose.components.resources)
        }

        desktopMain.dependencies {
            implementation(projects.sync)
            implementation(projects.qrConnect)
            implementation(projects.shared)
            implementation(projects.library.api)
            implementation(projects.library.impl)
            implementation(projects.drawing.api)
            implementation(projects.app.byCompose.common)
            implementation(projects.rendering.api)
            implementation(projects.rendering.impl)
            implementation(libs.kotlin.logging)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.jna)
            implementation(libs.jna.platform)
            compileOnly(libs.jbr.api)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.serialization.kotlinx.json)

            // decompose
            implementation(libs.decompose)
            implementation(libs.decompose.compose)

            // compose
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material)
            implementation(libs.compose.ui)
            //   implementation(compose.components.resources)
            implementation(libs.compose.components.ui.tooling.preview)
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
        javaHome =
            javaToolchains.compilerFor {
                languageVersion.set(JavaLanguageVersion.of(25))
                vendor.set(JvmVendorSpec.JETBRAINS)
            }.map { it.metadata.installationPath.asFile.absolutePath }.get()

        nativeDistributions {
            includeAllModules = false
            modules =
                arrayListOf(
                    "java.base",
                    "java.desktop",
                    "java.logging",
                    "java.sql",
                    "jdk.crypto.ec",
                )
            // Windows .exe собирается НЕ через jpackage (TargetFormat.Exe), а из
            // app-image (createReleaseDistributable) внешним инсталлятором Inno Setup
            // — см. installer/windows/notepen.iss. Причина: jpackage не умеет
            // добавить финальный чекбокс «Запустить NotePen» и opt-in галочку ярлыка.
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Deb,
            )
            packageName = "NotePen"
            packageVersion = providers.gradleProperty("app.version").getOrElse("1.0.0")
            description = "NotePen"
            copyright = "© 2025 KYamshanov. All rights reserved."
            vendor = "KYamshanov"

            // Регистрирует NotePen как обработчик поддерживаемых форматов, чтобы
            // его можно было выбрать приложением по умолчанию и открывать файл
            // «через него». macOS: пишется в CFBundleDocumentTypes (Info.plist),
            // путь приходит Apple Event "odoc" → java.awt.Desktop OpenFileHandler.
            // Linux (.deb): MIME + .desktop. Windows: реальную ассоциацию ставит
            // Inno Setup (installer/windows/notepen.iss) — расширить надо там же.
            fileAssociation(mimeType = "application/pdf", extension = "pdf", description = "PDF document")
            fileAssociation(mimeType = "image/png", extension = "png", description = "PNG image")
            fileAssociation(mimeType = "image/jpeg", extension = "jpg", description = "JPEG image")
            fileAssociation(mimeType = "image/jpeg", extension = "jpeg", description = "JPEG image")
            fileAssociation(mimeType = "application/epub+zip", extension = "epub", description = "EPUB book")
            fileAssociation(mimeType = "application/x-fictionbook+xml", extension = "fb2", description = "FictionBook")
            fileAssociation(mimeType = "application/vnd.comicbook+zip", extension = "cbz", description = "CBZ comic")
            fileAssociation(mimeType = "application/vnd.comicbook-rar", extension = "cbr", description = "CBR comic")

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
                    extraKeysRawXml =
                        """
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
            version.set("7.8.0")
            obfuscate.set(true)
            isEnabled.set(true)
            configurationFiles.from("proguard-rules.pro")
        }

        jvmArgs +=
            listOf(
                // Skiko (and JNA) load native libs via System.load(), which JDK 24+
                // flags as a restricted operation. Granting native access to the
                // unnamed module silences the warning and future-proofs against the
                // "blocked in a future release" deprecation.
                "--enable-native-access=ALL-UNNAMED",
                "-Dorg.slf4j.simpleLogger.defaultLogLevel=DEBUG",
                // PDFBox печатает WARN на каждый незашитый в PDF шрифт/глиф (подстановка
                // fallback-шрифта — штатное поведение при рендере чужих PDF, чинить нечего).
                // Поднимаем порог его логгеров до ERROR, чтобы не засорять вывод; настоящие
                // ошибки PDFBox по-прежнему видны. slf4j-simple ищет уровень вверх по точкам,
                // поэтому одной записи на org.apache.pdfbox хватает для всего поддерева.
                "-Dorg.slf4j.simpleLogger.log.org.apache.pdfbox=error",
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

// The Kotlin/JVM target auto-generates a `desktopRun` JvmRun task that IntelliJ uses
// when launching the app via "Run". It does NOT inherit jvmArgs from
// `compose.desktop.application {}` (which configures the separate `:run` task), so the
// --enable-native-access flag has to be applied here too. Otherwise IDE runs still emit
// the JDK 25 restricted-method warnings from Skiko/JNA native loaders.
tasks.withType<JavaExec>().configureEach {
    if (name == "desktopRun") {
        jvmArgs("--enable-native-access=ALL-UNNAMED")
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
