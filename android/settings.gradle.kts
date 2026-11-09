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

rootProject.name = "WeChatBridgeAndroid"

// `core` is plain Kotlin/JVM: ZIP parsing, Markdown, batches, collections and
// scenes, all unit-tested without an emulator. `app` is the Android shell.
include(":core")
include(":app")
