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