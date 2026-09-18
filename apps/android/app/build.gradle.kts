import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.isFile) localFile.inputStream().use(::load)
}

// Root .env is ignored by Git. It is read only for explicitly allowlisted local test inputs;
// it is never copied into BuildConfig, resources, the APK, or production configuration.
val dotEnvProperties = Properties().apply {
    val dotEnvFile = rootProject.file("../../.env")
    if (dotEnvFile.isFile) dotEnvFile.inputStream().use(::load)
}

fun localOrEnvironment(property: String, environment: String): String? =
    providers.environmentVariable(environment).orNull?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(property)?.takeIf { it.isNotBlank() }

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

fun environmentOrDotEnv(environment: String): String? =
    providers.environmentVariable(environment).orNull?.trim()?.takeIf { it.isNotBlank() }
        ?: dotEnvProperties.getProperty(environment)
            ?.trim()
            ?.removeSurrounding("\"")
            ?.removeSurrounding("'")
            ?.takeIf { it.isNotBlank() }

// CI may narrow native packaging for a device-specific distribution artifact. Local builds
// remain universal unless this property is explicitly supplied.
val configuredAbiFilters = providers.gradleProperty("olivia.abiFilters").orNull
    ?.split(',')
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    ?.toSet()
    ?.takeIf { it.isNotEmpty() }

val configuredMwdatApplicationId =
    localOrEnvironment("mwdat_application_id", "MWDAT_APPLICATION_ID")
val configuredMwdatClientToken =
    localOrEnvironment("mwdat_client_token", "MWDAT_CLIENT_TOKEN")

// Protected CI builds may inject the release signing material. Local builds keep the existing
// signing behavior unless all CI signing inputs are present.
val ciReleaseKeystorePath = providers.environmentVariable("ANDROID_RELEASE_KEYSTORE_PATH").orNull
    ?.takeIf { it.isNotBlank() }
val ciReleaseStorePassword = providers.environmentVariable("ANDROID_RELEASE_STORE_PASSWORD").orNull
    ?.takeIf { it.isNotBlank() }
val ciReleaseKeyAlias = providers.environmentVariable("ANDROID_RELEASE_KEY_ALIAS").orNull
    ?.takeIf { it.isNotBlank() }
val ciReleaseKeyPassword = providers.environmentVariable("ANDROID_RELEASE_KEY_PASSWORD").orNull
    ?.takeIf { it.isNotBlank() }
val ciReleaseSigningReady = listOf(
    ciReleaseKeystorePath,
    ciReleaseStorePassword,
    ciReleaseKeyAlias,
    ciReleaseKeyPassword,
).all { it != null }
val signDeveloperWithReleaseKey =
    providers.gradleProperty("olivia.signDeveloperWithRelease").orNull == "true"
val debugAppCheckUsesPlayIntegrity =
    providers.gradleProperty("olivia.debugAppCheckProvider").orNull == "play-integrity"

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

    signingConfigs {
        if (ciReleaseSigningReady) {
            create("ciRelease") {
                storeFile = rootProject.file(requireNotNull(ciReleaseKeystorePath))
                storePassword = requireNotNull(ciReleaseStorePassword)
                keyAlias = requireNotNull(ciReleaseKeyAlias)
                keyPassword = requireNotNull(ciReleaseKeyPassword)
            }
        }
    }

    defaultConfig {
        applicationId = "com.olivia.companion"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        configuredAbiFilters?.let { filters ->
            ndk { abiFilters.addAll(filters) }
        }
        // Local Whisper transcription (BE-12/BE-08) only ships an arm64 build of
        // the vendored whisper.cpp; see third_party/whisper-cpp/NOTICE.md.
        externalNativeBuild {
            cmake {
                abiFilters += "arm64-v8a"
                arguments += "-DWHISPER_LIB_DIR=${rootProject.projectDir.absolutePath}/third_party/whisper-cpp"
                // whisper.cpp's own CMakeLists only turns on -O3/LTO when CMAKE_BUILD_TYPE
                // != Debug. AGP maps the Android debug build type to a Debug native build by
                // default, which measured ~1000x slower on-device for this workload (nobody
                // debugs whisper.cpp's C++ with a native debugger here) — force Release always.
                arguments += "-DCMAKE_BUILD_TYPE=Release"
            }
        }
        buildConfigField("String", "PICOVOICE_ACCESS_KEY", buildConfigString(
            localOrEnvironment("picovoice_access_key", "PICOVOICE_ACCESS_KEY").orEmpty(),
        ))
        buildConfigField("String", "PICOVOICE_KEYWORD_PATH", buildConfigString(
            localProperties.getProperty("picovoice_keyword_path") ?: "olivia_android.ppn",
        ))
        buildConfigField("String", "PICOVOICE_PORCUPINE_MODEL_PATH", buildConfigString(
            localProperties.getProperty("picovoice_porcupine_model_path") ?: "porcupine_params_pt.pv",
        ))
        buildConfigField("String", "PICOVOICE_RHINO_CONTEXT_PATH", buildConfigString(
            localProperties.getProperty("picovoice_rhino_context_path")
                ?: "olivia-rhino_pt_android_v4_1_0.rhn",
        ))
        buildConfigField("String", "PICOVOICE_RHINO_MODEL_PATH", buildConfigString(
            localProperties.getProperty("picovoice_rhino_model_path") ?: "rhino_params_pt.pv",
        ))
        // Release uses the registered integration. The debug override below intentionally
        // switches local development builds to DAT Developer Mode (0/0), which avoids app
        // attestation and requires Developer Mode enabled in Meta AI.
        manifestPlaceholders["mwdatApplicationId"] =
            configuredMwdatApplicationId ?: "0"
        manifestPlaceholders["mwdatClientToken"] =
            configuredMwdatClientToken ?: "0"
        buildConfigField(
            "boolean",
            "MWDAT_USE_MOCK_DEVICE_KIT",
            (localOrEnvironment("mwdat_mock_device_kit", "MWDAT_MOCK_DEVICE_KIT") == "true").toString(),
        )
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Live tests must be selected explicitly; default instrumentation is offline.
        testInstrumentationRunnerArguments["class"] = "com.olivia.companion.infra.InfraConfigurationIntegrationTest"
        // This allowlist is intentionally limited to instrumentation-only live AI inputs.
        // It never configures the installed app. Keeping all three values together in .env
        // makes a local, explicitly enabled probe reproducible without exporting secrets in
        // every PowerShell session.
        if (environmentOrDotEnv("OLIVIA_LIVE_AI_ENABLED") == "true") {
            mapOf(
                "oliviaLiveAiEnabled" to "OLIVIA_LIVE_AI_ENABLED",
                "oliviaAiModel" to "OLIVIA_AI_MODEL",
                "firebaseAppCheckDebugSecret" to "FIREBASE_APP_CHECK_DEBUG_TOKEN",
            ).forEach { (argument, variable) ->
                testInstrumentationRunnerArguments[argument] =
                    requireNotNull(environmentOrDotEnv(variable)) {
                        "Missing infrastructure test configuration: $variable"
                    }
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
        debug {
            // Use the locally configured Wearables integration for the physical hackathon APK.
            // Developer Mode remains enabled on the device, but DAT 0.9.0 still requires the
            // client token while completing the registration handshake.
            if (ciReleaseSigningReady && signDeveloperWithReleaseKey) {
                signingConfig = signingConfigs.getByName("ciRelease")
            }
            buildConfigField(
                "boolean",
                "OLIVIA_USE_PLAY_INTEGRITY_APP_CHECK",
                debugAppCheckUsesPlayIntegrity.toString(),
            )
        }
        create("debugV2") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".debugv2"
            versionNameSuffix = "-debug-v2"
            matchingFallbacks += listOf("debug")
            // This APK is intentionally physical-only so it cannot silently fall back to
            // MockDeviceKit while comparing it against the standalone DAT diagnostic.
            buildConfigField("boolean", "MWDAT_USE_MOCK_DEVICE_KIT", "false")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (ciReleaseSigningReady) signingConfig = signingConfigs.getByName("ciRelease")
            buildConfigField("boolean", "OLIVIA_USE_PLAY_INTEGRITY_APP_CHECK", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // OpenCV and Facebook native dependencies ship the same filename, but only the
        // Facebook/DAT runtime is ABI-compatible with libfbjni. A generated jniLibs source below
        // supplies that copy deterministically instead of letting pickFirst select OpenCV's one.
        jniLibs.pickFirsts += "lib/*/libc++_shared.so"
    }

    androidResources {
        // Already-quantized ggml binaries barely shrink under AAPT compression, and storing
        // them uncompressed avoids paying that decompression cost on every model load.
        noCompress += "bin"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/whisper/CMakeLists.txt")
        }
    }
}

// OpenCV's AAR bundles a libc++ runtime built with a different NDK. Keep the DAT/fbjni copy for
// all ABIs; this is generated under build/ and never becomes a repository binary.
val fbjniCppDir = layout.buildDirectory.dir("generated/fbjni-cpp")
val copyFbjniCpp = tasks.register<Copy>("copyFbjniCppRuntime") {
    val runtime = configurations.named("glassesDebugRuntimeClasspath")
    from(runtime.map { files ->
        files.files
            .filter { it.name.startsWith("fbjni-") && it.extension == "aar" }
            .map(::zipTree)
    }) {
        include("jni/**/libc++_shared.so")
        eachFile { path = path.substringAfter("jni/") }
        includeEmptyDirs = false
    }
    into(fbjniCppDir)
}
android.sourceSets.named("glasses") { jniLibs.srcDir(fbjniCppDir) }
tasks.matching { it.name in setOf("mergeGlassesDebugJniLibFolders", "mergeGlassesDebugNativeLibs") }.configureEach {
    dependsOn(copyFbjniCpp)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // DAT remains exclusive to the physical-glasses variant; :core stays SDK-free.
    // Keep DAT's libc++ runtime first when the OpenCV AAR contributes its own copy.
    "glassesImplementation"(libs.opencv)
    "glassesImplementation"(libs.mwdat.core)
    "glassesImplementation"(libs.mwdat.camera)
    "glassesImplementation"(libs.mwdat.mockdevice)
    "glassesImplementation"(platform(libs.firebase.bom))
    "glassesImplementation"(libs.firebase.ai)
    "glassesImplementation"(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    // Test APK only: no debug provider is shipped in the release APK.
    androidTestImplementation(platform(libs.firebase.bom))
    androidTestImplementation(libs.firebase.ai)
    // The test APK needs this registrar as well as the helper; debugImplementation alone is
    // packaged only with the target APK.
    androidTestImplementation(libs.firebase.appcheck.debug)
    androidTestImplementation(libs.firebase.appcheck.debug.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    implementation(project(":core"))
    // The BOM only supplies versions. Firebase binaries remain glasses-only.
    implementation(platform(libs.firebase.bom))

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.porcupine.android)
    implementation(libs.rhino.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

tasks.register<Exec>("installMockDebugAdb") {
    group = "install"
    description = "Installs mock debug with adb, skipping AGP's device ABI matcher."
    dependsOn("assembleMockDebug")
    commandLine(rootProject.layout.projectDirectory.file("install-mock.sh").asFile.absolutePath)
}
