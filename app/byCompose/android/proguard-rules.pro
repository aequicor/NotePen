# NotePen Android — R8 release rules.
# Приоритеты: минимальный размер APK + обфускация (безопасность).
# Базовые правила берутся из proguard-android-optimize.txt; здесь — только
# то, что R8 не может вывести сам (рефлексия, сериализация, ServiceLoader).

# --- Источник для читаемых стектрейсов из обфусцированного билда ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- kotlinx.serialization ---
# Нужны для генерации/поиска KSerializer через рефлексию по дженерикам.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod

# Сохраняем сериализаторы @Serializable-классов всего приложения.
-if @kotlinx.serialization.Serializable class ru.kyamshanov.notepen.**
-keepclassmembers class ru.kyamshanov.notepen.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ru.kyamshanov.notepen.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Синтетический $serializer, который генерирует плагин сериализации.
-keepclassmembers,allowobfuscation class ru.kyamshanov.notepen.** {
    static **$* *;
}
-keep,includedescriptorclasses class kotlinx.serialization.json.** { *; }

# --- Ktor client (CIO) + coroutines ---
# Ktor/coroutines обращаются к volatile-полям по имени через AtomicFieldUpdater.
-keepclassmembernames class io.ktor.** { volatile <fields>; }
-keepclassmembernames class kotlinx.coroutines.** { volatile <fields>; }
-keepclasseswithmembers class io.ktor.client.engine.cio.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**

# --- Decompose / Essenty ---
# Восстановление состояния навигации сериализует конфиги компонентов.
-keep class com.arkivanov.decompose.** { *; }
-keep class com.arkivanov.essenty.** { *; }

# Подавляем шум по отсутствующим опциональным зависимостям.
-dontnote kotlinx.serialization.**
