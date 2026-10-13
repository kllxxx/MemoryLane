pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MemoryLane"
include(":app")

include(":feature:swipe:api")
include(":feature:swipe:impl")
include(":feature:achievements:api")
include(":feature:achievements:impl")
include(":feature:profile:api")
include(":feature:profile:impl")

include(":core:photos:api")
include(":core:photos:impl")
include(":core:ui")
include(":core:data:api")
include(":core:data:impl")

include(":lib:lazycardstack")
