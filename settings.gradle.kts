rootProject.name = "NotePen"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":app:byCompose:android")
include(":app:byCompose:desktop")
include(":app:byCompose:common")
include(":app:byCompose:theme")
include(":app:byCompose:uikit")
include(":app:byCompose:blur")
include(":shared")
include(":sync")
include(":qr-connect")
include(":server")
include(":rendering:api")
include(":rendering:impl")
include(":reflow:api")
include(":reflow:impl")
include(":drawing:api")
include(":drawing:impl")
include(":tools:marker")

