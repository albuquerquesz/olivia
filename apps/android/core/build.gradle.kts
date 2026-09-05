import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Kotlin puro de propósito: sem AGP e sem Android aqui, o domínio da Olivia não
// consegue importar tipos do DAT nem do framework nem por acidente.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        explicitApi()
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(libs.kotlinx.coroutines.core)
}
