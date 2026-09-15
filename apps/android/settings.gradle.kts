import java.util.Properties

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val localProperties = Properties().apply {
    val localFile = file("local.properties")
    if (localFile.isFile) localFile.inputStream().use(::load)
}
val datPackageToken = providers.environmentVariable("GITHUB_TOKEN").orNull
    ?.takeIf { it.isNotBlank() }
    ?: localProperties.getProperty("github_token")?.takeIf { it.isNotBlank() }

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Mock builds remain independent of this private repository and its credential.
        if (datPackageToken != null) {
            maven {
                url = uri("https://maven.pkg.github.com/facebook/meta-wearables-dat-android")
                credentials {
                    username = "x-access-token"
                    password = datPackageToken
                }
            }
        }
    }
}

rootProject.name = "olivia-android"

include(":app")
include(":core")
