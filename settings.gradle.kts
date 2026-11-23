@file:Suppress("UnstableApiUsage")
pluginManagement {
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
        // The libre Jitsi SDK is built from source by scripts/build-jitsi-libre-sdk.sh.
        // Keep this first so a locally published 13.1.1 cannot be shadowed by a remote AAR.
        mavenLocal()
        google()
        mavenCentral()
    }
}

rootProject.name = "HelmChatRisen"
include(":app")
