import {
  mkdtemp,
  readFile,
  realpath,
  rm,
  stat,
  writeFile,
} from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import {
  childEnvironment,
  firebasePublicConfig,
  InfraConfigError,
  readInfraConfig,
} from "../../packages/env/src/infra";
import { execute, HarnessFailure, withCleanup } from "./process";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const allowed = [
  "unit",
  "integration-offline",
  "integration-android",
  "integration-live-ai",
  "integration-whisper",
] as const;
type Suite = (typeof allowed)[number];

async function main(suite: Suite): Promise<void> {
  const mode =
    suite === "integration-live-ai"
      ? "live-ai"
      : suite === "integration-whisper"
        ? "whisper"
        : "offline";
  const config = readInfraConfig({ ...process.env, OLIVIA_TEST_MODE: mode });
  const env = childEnvironment(process.env);
  if (suite === "unit") {
    await execute({
      argv: [
        process.execPath,
        "--no-env-file",
        "test",
        "infra/testing/tests/config.test.ts",
        "infra/testing/tests/process.test.ts",
      ],
      cwd: root,
      env,
    });
    return;
  }
  if (suite === "integration-android" || suite === "integration-live-ai") {
    if (config.mode === "live-ai") {
      let value: unknown;
      try {
        value = JSON.parse(
          await readFile(resolve(root, config.firebaseConfigPath), "utf8"),
        );
      } catch {
        throw new InfraConfigError("OLIVIA_FIREBASE_CONFIG_PATH");
      }
      const firebase = firebasePublicConfig(
        value,
        config.projectId,
        suite === "integration-live-ai" ? "com.olivia.companion" : "com.olivia.companion.mock",
      );
      Object.assign(env, {
        OLIVIA_LIVE_AI_ENABLED: "true",
        OLIVIA_FIREBASE_PROJECT_ID: firebase.projectId,
        OLIVIA_FIREBASE_APP_ID: firebase.applicationId,
        OLIVIA_FIREBASE_API_KEY: firebase.apiKey,
        FIREBASE_APP_CHECK_DEBUG_TOKEN: config.debugToken.reveal(),
        OLIVIA_AI_MODEL: config.model,
      });
    }
    // Invoke wrapper class directly: no cmd.exe interpolation or credentials in argv.
    const java = env.JAVA_HOME
      ? join(
          env.JAVA_HOME,
          "bin",
          process.platform === "win32" ? "java.exe" : "java",
        )
      : "java";
    const testClass =
      suite === "integration-live-ai"
        ? "FirebaseAccessIntegrationTest"
        : "InfraConfigurationIntegrationTest";
    const androidVariant = suite === "integration-live-ai" ? "Glasses" : "Mock";
    await execute({
      cwd: join(root, "apps/android"),
      env,
      timeoutMs: 900_000,
      argv: [
        java,
        "-classpath",
        "gradle/wrapper/gradle-wrapper.jar",
        "org.gradle.wrapper.GradleWrapperMain",
        "--no-daemon",
        "--no-configuration-cache",
        `:app:connected${androidVariant}DebugAndroidTest`,
        `-Pandroid.testInstrumentationRunnerArguments.class=com.olivia.companion.infra.${testClass}`,
      ],
    });
    return;
  }
  const project = `olivia-test-${crypto.randomUUID().replaceAll("-", "")}`;
  const temporary = await mkdtemp(join(tmpdir(), "olivia-infra-"));
  // Bun.spawn on Windows does not discover Docker Desktop's compose plugin
  // through `docker compose`; Docker Desktop exposes the standalone command.
  // Keep the POSIX path compatible with CI while making local execution
  // deterministic on both platforms.
  const composeExecutable = process.platform === "win32" ? "docker-compose" : "docker";
  const composePrefix = process.platform === "win32" ? [composeExecutable] : [composeExecutable, "compose"];
  const base = [
    ...composePrefix,
    "--project-name",
    project,
    "--file",
    join(root, "infra/testing/compose.yaml"),
  ];
  try {
    if (config.mode === "whisper") {
      const model = await realpath(resolve(root, config.modelPath));
      const fixture = await realpath(resolve(root, config.fixturePath));
      if (!(await stat(model)).isFile() || !(await stat(fixture)).isFile())
        throw new InfraConfigError("OLIVIA_WHISPER_INPUTS");
      // Only exact files mounted. Never mount the source directory or root .env.
      const override = join(temporary, "compose.json");
      await writeFile(
        override,
        JSON.stringify({
          services: {
            whisper: {
              volumes: [
                {
                  type: "bind",
                  source: model,
                  target: "/inputs/model.bin",
                  read_only: true,
                },
                {
                  type: "bind",
                  source: fixture,
                  target: "/inputs/fixture",
                  read_only: true,
                },
              ],
              environment: {
                OLIVIA_WHISPER_MODEL_SHA256: config.modelSha256,
                OLIVIA_WHISPER_FIXTURE_SHA256: config.fixtureSha256,
                OLIVIA_WHISPER_EXPECTED_TEXT: config.expectedText.reveal(),
              },
            },
          },
        }),
        { mode: 0o600 },
      );
      base.push("--file", override, "--profile", "whisper");
    } else base.push("--profile", "offline");
    const compose = (...args: string[]) =>
      execute({
        argv: [...base, ...args],
        cwd: temporary,
        env,
        timeoutMs: 900_000,
      });
    await withCleanup(
      async () => {
        if (config.mode === "whisper") {
          await compose("build", "whisper");
          await compose("run", "--rm", "--no-deps", "whisper");
        } else {
          await compose(
            "build",
            "core-contracts",
            "core-network",
            "harness-unit",
            "provider-stub",
            "network-probe",
          );
          await compose("run", "--rm", "--no-deps", "core-contracts");
          await compose("run", "--rm", "--no-deps", "harness-unit");
          await compose(
            "up",
            "--detach",
            "--wait",
            "--wait-timeout",
            "45",
            "provider-stub",
          );
          await compose("run", "--rm", "--no-deps", "core-network");
          await compose("run", "--rm", "--no-deps", "network-probe");
        }
      },
      () => compose("down", "--volumes", "--remove-orphans", "--timeout", "10"),
    );
  } finally {
    // The only recursively removed directory is the fresh mkdtemp allocation above.
    await rm(temporary, { recursive: true, force: true });
  }
}

const requested = process.argv[2];
const started = performance.now();
let status = "passed";
let code = "OK";
if (!allowed.includes(requested as Suite)) {
  status = "failed";
  code = "INVALID_SUITE";
} else
  try {
    await main(requested as Suite);
  } catch (error) {
    status = "failed";
    code =
      error instanceof InfraConfigError
        ? "CONFIG_INVALID"
        : error instanceof HarnessFailure
          ? error.code
          : "HARNESS_FAILED";
  }
// Fixed schema only. Never serialize errors, process output, paths, credentials or media.
console.log(
  JSON.stringify({
    schemaVersion: 1,
    suite: allowed.includes(requested as Suite) ? requested : "unknown",
    status,
    code,
    durationMs: Math.round(performance.now() - started),
  }),
);
if (status !== "passed") process.exitCode = 1;
