pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
    versionCatalogs {
        create("libs") { from(files("../../apps/android/gradle/libs.versions.toml")) }
    }
}

// Build separado reutiliza o módulo real, sem configurar :app, AGP ou SDK Android.
rootProject.name = "olivia-core-integration"
include(":core")
project(":core").projectDir = file("../../apps/android/core")
