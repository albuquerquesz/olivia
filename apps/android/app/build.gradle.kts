import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.isFile) localFile.inputStream().use(::load)
}

fun localOrEnvironment(property: String, environment: String): String? =
    providers.environmentVariable(environment).orNull?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(property)?.takeIf { it.isNotBlank() }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.olivia.companion"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.olivia.companion"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        // Developer Mode accepts 0/0. Registered values are only supplied by ignored
        // local.properties or CI; source control never contains either credential.
        manifestPlaceholders["mwdatApplicationId"] =
            localOrEnvironment("mwdat_application_id", "MWDAT_APPLICATION_ID") ?: "0"
        manifestPlaceholders["mwdatClientToken"] =
            localOrEnvironment("mwdat_client_token", "MWDAT_CLIENT_TOKEN") ?: "0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Live tests must be selected explicitly; default instrumentation is offline.
        testInstrumentationRunnerArguments["class"] = "com.olivia.companion.infra.InfraConfigurationIntegrationTest"
        if (System.getenv("OLIVIA_LIVE_AI_ENABLED") == "true") {
            mapOf(
                "oliviaLiveAiEnabled" to "OLIVIA_LIVE_AI_ENABLED",
                "oliviaFirebaseProjectId" to "OLIVIA_FIREBASE_PROJECT_ID",
                "oliviaFirebaseAppId" to "OLIVIA_FIREBASE_APP_ID",
                "oliviaFirebaseApiKey" to "OLIVIA_FIREBASE_API_KEY",
                "oliviaAiModel" to "OLIVIA_AI_MODEL",
                "firebaseAppCheckDebugSecret" to "FIREBASE_APP_CHECK_DEBUG_TOKEN",
            ).forEach { (argument, variable) ->
                testInstrumentationRunnerArguments[argument] =
                    requireNotNull(System.getenv(variable)?.takeIf { it.isNotBlank() }) { "Missing infrastructure test configuration: $variable" }
            }
        }
    }

    flavorDimensions += "device"
    productFlavors {
        create("mock") {
            dimension = "device"
            applicationIdSuffix = ".mock"
            versionNameSuffix = "-mock"
        }
        create("glasses") {
            dimension = "device"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // DAT remains exclusive to the physical-glasses variant; :core stays SDK-free.
    "glassesImplementation"(libs.mwdat.core)
    "glassesImplementation"(libs.mwdat.camera)
    "glassesImplementation"(libs.mwdat.mockdevice)
    "glassesImplementation"(platform(libs.firebase.bom))
    "glassesImplementation"(libs.firebase.ai)
    "glassesImplementation"(libs.firebase.appcheck.playintegrity)

    // Test APK only: no Firebase/debug provider shipped in the product APK.
    androidTestImplementation(platform(libs.firebase.bom))
    androidTestImplementation(libs.firebase.ai)
    androidTestImplementation(libs.firebase.appcheck.debug.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
