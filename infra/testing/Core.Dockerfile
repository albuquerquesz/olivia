FROM eclipse-temurin:17.0.18_8-jdk-jammy

RUN useradd --create-home --uid 10001 runner
WORKDIR /workspace
ENV GRADLE_USER_HOME=/home/runner/.gradle

# Allowlist: não copiar .env, local.properties, app Android ou o repositório inteiro.
COPY --chown=runner:runner apps/android/gradlew apps/android/gradlew
COPY --chown=runner:runner apps/android/gradle/wrapper apps/android/gradle/wrapper
COPY --chown=runner:runner apps/android/gradle/libs.versions.toml apps/android/gradle/libs.versions.toml
COPY --chown=runner:runner apps/android/core/build.gradle.kts apps/android/core/build.gradle.kts
COPY --chown=runner:runner apps/android/core/src apps/android/core/src
COPY --chown=runner:runner infra/testing/settings.gradle.kts infra/testing/settings.gradle.kts
RUN chown runner:runner /workspace /workspace/infra/testing /workspace/apps/android/core
USER runner

# Compila e resolve dependências na preparação, mas não executa testes.
RUN sh apps/android/gradlew -p infra/testing --no-daemon -Pkotlin.compiler.execution.strategy=in-process :core:prepareOfflineTests

ENTRYPOINT ["sh", "apps/android/gradlew", "-p", "infra/testing", "--offline", "--no-daemon", "-Pkotlin.compiler.execution.strategy=in-process"]
CMD [":core:test", ":core:integrationTest", "--rerun-tasks"]
