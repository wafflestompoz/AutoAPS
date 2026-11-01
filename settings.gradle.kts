pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AutoAPS"

// Pure-Kotlin Nightscout client, parsing and formatting. It is its own build so it
// can be compiled and unit-tested without the Android SDK (`cd core && gradle test`).
includeBuild("core")
include(":app")
