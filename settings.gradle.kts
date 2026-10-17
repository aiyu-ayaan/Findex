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
        // Only for 7-Zip-JBinding-4Android (native 7-Zip: RAR5, every zip method).
        maven("https://jitpack.io") { content { includeGroup("com.github.omicronapps") } }
    }
}

rootProject.name = "Findex"
include(":app")
include(":core")
include(":ui")
