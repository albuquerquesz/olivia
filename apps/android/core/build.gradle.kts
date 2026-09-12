import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
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
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test-junit"))
}

tasks.test {
    exclude("**/*IntegrationTest.class")
    exclude("**/*InfrastructureTest.class")
}

val integrationTest by tasks.registering(Test::class) {
    description = "Integra contratos concretos, adapters de fixture e codec JSON."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/*IntegrationTest.class")
    shouldRunAfter(tasks.test)
}

tasks.check { dependsOn(integrationTest) }

tasks.register<Test>("infrastructureTest") {
    description = "HTTP real entre runner JVM e stub Docker isolado."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/*InfrastructureTest.class")
}

// Prepara o runner offline sem executar testes durante a criação da imagem.
tasks.register("prepareOfflineTests") {
    dependsOn(tasks.testClasses)
    doLast { configurations.testRuntimeClasspath.get().files }
}
