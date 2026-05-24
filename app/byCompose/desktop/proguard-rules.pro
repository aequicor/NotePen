# proguard-rules.pro
-dontwarn **
-ignorewarnings
-dontnote **

-keep class kotlinx.coroutines.swing.*
-keep class com.arkivanov.decompose.**
# Стандартное правило для enum: без него ProGuard ломает values()/valueOf и
# $VALUES, из-за чего EnumMap(enumClass) падает с NPE "keyUniverse is null"
# (краш в com.jetbrains.* / JBR API при старте — setupJbrTitleBar).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
# JBR API (com.jetbrains.*) связывается с реализацией в рантайме рефлексивно.
-keep class com.jetbrains.** { *; }
-dontwarn com.jetbrains.**
-keep class io.ktor.** { *; }
# JDBC-драйвер sqlite регистрируется рефлексивно через META-INF/services
# (JdbcSqliteDriver → DriverManager). Статических ссылок нет, поэтому без keep
# ProGuard вырезает org.sqlite.* целиком и release падает при открытии sync-БД
# в main() (createSyncDatabaseJvm).
-keep class org.sqlite.** { *; }
# JNA (com.sun.jna.**) связывает нативный код ИСКЛЮЧИТЕЛЬНО по именам, поэтому
# обфускация ломает release тремя способами:
#  • com.sun.jna.Native в своём <clinit> зовёт native getNativeVersion()/
#    getAPIChecksum() (сверка версии jnidispatch). JNI ищет символ по исходному
#    имени Java_com_sun_jna_Native_…, а переименованный метод (в стектрейсе —
#    com.sun.a.x.l) не находится → UnsatisfiedLinkError при первом обращении к JNA
#    во время рендера (Wintab / Windows pointer-hook / macOS-жесты).
#  • Library-интерфейсы: имя метода = имя функции в нативной библиотеке (WTOpenA…).
#  • Structure-классы: getFieldOrder() сверяет поля по строковым именам ("lcName"…),
#    которые не обфусцируются, тогда как сами @JvmField-поля — да → раскладка рушится.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.Library { *; }
-keep class * implements com.sun.jna.Callback { *; }
-keepclassmembers class * extends com.sun.jna.Structure {
    <fields>;
    <init>(...);
}
# Apache PDFBox логирует через Apache Commons Logging: в <clinit> PDFBox-классов
# LogFactory.getLog() ищет реализацию по строке
# Class.forName("org.apache.commons.logging.impl.LogFactoryImpl"). Статической
# ссылки на LogFactoryImpl нет, поэтому ProGuard её вырезает/переименовывает →
# ExceptionInInitializerError (ClassNotFoundException) при первом рендере PDF.
# Keep сохраняет и фабрику, и реализацию под исходными именами.
-keep class org.apache.commons.logging.** { *; }
# FontBox/PDFBox грузят встроенные ресурсы (предопределённые CMap'ы Identity-H и
# др.) ОТНОСИТЕЛЬНО пакета класса-загрузчика: getResourceAsStream("Identity-H")
# резолвится в <пакет загрузчика>/Identity-H. Сами ресурсы лежат в
# org/apache/fontbox/cmap/ и при обфускации не двигаются, а класс-загрузчик
# переезжает в org/apache/a/c/ → относительный путь промахивается → IOException
# "Could not find referenced cmap stream Identity-H" при рендере PDF. Сохраняем
# ИМЕНА ПАКЕТОВ (имена классов и shrinking при этом продолжают работать), чтобы
# относительный lookup по-прежнему попадал в каталог с ресурсами.
-keeppackagenames org.apache.fontbox.**
-keeppackagenames org.apache.pdfbox.**
# JBIG2-кодированные изображения (типичны для сканированных PDF) декодирует
# ImageIO-плагин org.apache.pdfbox:jbig2-imageio. Он подключён как runtimeOnly и
# регистрируется ИСКЛЮЧИТЕЛЬНО через META-INF/services/javax.imageio.spi.ImageReaderSpi
# — статических ссылок на JBIG2ImageReaderSpi в коде нет (как у sqlite-драйвера выше).
# Поэтому ProGuard вырезает/обфусцирует класс, а файл сервиса по-прежнему указывает на
# исходное имя → ImageIO падает с "Provider org.apache.pdfbox.jbig2.JBIG2ImageReaderSpi
# not found" при открытии такого PDF. Keep сохраняет SPI и декодер под исходными именами.
-keep class org.apache.pdfbox.jbig2.** { *; }
-keep class ru.kyamshanov.notepen.sync.domain.model.** { *; }
-keep class ru.kyamshanov.notepen.annotation.domain.model.** { *; }
-keepclassmembers class ru.kyamshanov.notepen.** {
    *** Companion;
}
-keepclasseswithmembers class ru.kyamshanov.notepen.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Синтетический $serializer плагина сериализации — для всех пакетов приложения
# (mainscreen.infrastructure.dto, shortcuts.domain.model и др.), а не только явно
# перечисленных выше.
-keepclassmembers,allowobfuscation class ru.kyamshanov.notepen.** {
    static **$* *;
}

# Signature нужен для сериализаторов дженерик-типов (List<T>, Map<K,V>).
-keepattributes Annotation, InnerClasses, Signature
-dontnote kotlinx.serialization.AnnotationsKt
-dontnote kotlinx.serialization.SerializationKt

# Keep Serializers
# https://github.com/Kotlin/kotlinx.serialization/issues/1105
-keep,includedescriptorclasses class **.data.model.* { *; }
-keepclassmembers class **.data.model.* {
    *** Companion;
}
-keepclasseswithmembers class **.data.model.* {
    kotlinx.serialization.KSerializer serializer(...);
}

# When kotlinx.serialization.json.JsonObjectSerializer occurs

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# kotlin-logging тянет в jar опциональный logback-адаптер, но самого logback
# на classpath нет (логирование идёт через slf4j-simple). Фаза оптимизации
# ProGuard падает с IncompleteClassHierarchyException, пытаясь вычислить
# суперкласс LogbackLogEvent (его база ch.qos.logback.* отсутствует).
# Добавлять logback в рантайм нельзя — это раздувает бандл и даёт конфликт
# slf4j-биндингов. Поэтому отключаем именно оптимизацию байткода и оставляем
# главное: shrinking (вес) + обфускацию (безопасность).
-dontwarn ch.qos.logback.**
-dontoptimize