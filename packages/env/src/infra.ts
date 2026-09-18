/** Pure configuration parser. Does not load server env, connect a DB or read files. */
export type InfraMode = "offline" | "live-ai" | "whisper";
export type Environment = Readonly<Record<string, string | undefined>>;

export class InfraConfigError extends Error {
  constructor(readonly field: string) {
    super(`Configuração de infraestrutura inválida: ${field}`);
    this.name = "InfraConfigError";
  }
}

/** Explicit reveal at a process boundary; accidental JSON/inspection stays redacted. */
export class Secret {
  #value: string;
  constructor(value: string) { this.#value = value; }
  reveal(): string { return this.#value; }
  toString(): string { return "[REDACTED]"; }
  toJSON(): string { return "[REDACTED]"; }
  [Symbol.for("nodejs.util.inspect.custom")](): string { return "[REDACTED]"; }
}

export type InfraConfig =
  | { mode: "offline" }
  | {
      mode: "live-ai"; model: string; projectId: string; firebaseConfigPath: string;
      debugToken: Secret; maxRequests: number; maxEstimatedCostUsd: number;
      estimatedCostPerRequestUsd: number;
    }
  | {
      mode: "whisper"; modelPath: string; modelSha256: string;
      fixturePath: string; fixtureSha256: string; expectedText: Secret;
    };

function required(env: Environment, key: string): string {
  const value = env[key]?.trim();
  if (!value || /[\r\n\0]/.test(value)) throw new InfraConfigError(key);
  return value;
}

function positive(env: Environment, key: string, fallback?: number): number {
  const value = env[key]?.trim() || (fallback === undefined ? "" : String(fallback));
  if (!/^(?:\d+)(?:\.\d+)?$/.test(value)) throw new InfraConfigError(key);
  const number = Number(value);
  if (!Number.isFinite(number) || number <= 0) throw new InfraConfigError(key);
  return number;
}

function hash(env: Environment, key: string): string {
  const value = required(env, key);
  if (!/^[a-f0-9]{64}$/i.test(value)) throw new InfraConfigError(key);
  return value.toLowerCase();
}

export function readInfraConfig(env: Environment): InfraConfig {
  const mode = env.OLIVIA_TEST_MODE?.trim() || "offline";
  // Offline ignores secrets, including stale credentials loaded by Bun from .env.
  if (mode === "offline") return { mode };
  if (mode === "live-ai") {
    const projectId = required(env, "OLIVIA_FIREBASE_PROJECT_ID");
    if (!/^[a-z][a-z0-9-]{4,28}[a-z0-9]$/.test(projectId)) throw new InfraConfigError("OLIVIA_FIREBASE_PROJECT_ID");
    const model = required(env, "OLIVIA_AI_MODEL");
    if (model !== "gemini-3.5-flash-lite") throw new InfraConfigError("OLIVIA_AI_MODEL");
    const maxRequests = positive(env, "OLIVIA_LIVE_AI_MAX_REQUESTS", 12);
    if (!Number.isInteger(maxRequests) || maxRequests < 3 || maxRequests > 12) throw new InfraConfigError("OLIVIA_LIVE_AI_MAX_REQUESTS");
    const maxEstimatedCostUsd = positive(env, "OLIVIA_LIVE_AI_MAX_ESTIMATED_COST_USD");
    const estimatedCostPerRequestUsd = positive(env, "OLIVIA_LIVE_AI_ESTIMATED_COST_PER_REQUEST_USD");
    if (maxRequests * estimatedCostPerRequestUsd > maxEstimatedCostUsd) throw new InfraConfigError("OLIVIA_LIVE_AI_MAX_ESTIMATED_COST_USD");
    return {
      mode, model, projectId, maxRequests, maxEstimatedCostUsd, estimatedCostPerRequestUsd,
      firebaseConfigPath: required(env, "OLIVIA_FIREBASE_CONFIG_PATH"),
      debugToken: new Secret(required(env, "FIREBASE_APP_CHECK_DEBUG_TOKEN")),
    };
  }
  if (mode === "whisper") return {
    mode, modelPath: required(env, "OLIVIA_WHISPER_MODEL_PATH"),
    modelSha256: hash(env, "OLIVIA_WHISPER_MODEL_SHA256"),
    fixturePath: required(env, "OLIVIA_WHISPER_FIXTURE_PATH"),
    fixtureSha256: hash(env, "OLIVIA_WHISPER_FIXTURE_SHA256"),
    expectedText: new Secret(required(env, "OLIVIA_WHISPER_EXPECTED_TEXT")),
  };
  throw new InfraConfigError("OLIVIA_TEST_MODE");
}

/** Never spread process.env into Docker/Gradle. No PAT, database URL or cloud admin key. */
export function childEnvironment(env: Environment): Record<string, string> {
  const selected: Record<string, string> = {};
  for (const key of ["PATH", "Path", "SystemRoot", "SYSTEMROOT", "COMSPEC", "PATHEXT", "TEMP", "TMP", "HOME", "USERPROFILE", "JAVA_HOME", "ANDROID_HOME", "ANDROID_SDK_ROOT", "ANDROID_SERIAL", "DOCKER_HOST", "DOCKER_CONTEXT", "DOCKER_CONFIG"]) {
    if (env[key]) selected[key] = env[key]!;
  }
  return selected;
}

export type FirebasePublicConfig = { projectId: string; applicationId: string; apiKey: string };

/** Accepts only one of the two registered Olivia Android package names. */
export function firebasePublicConfig(
  value: unknown,
  projectId: string,
  packageName: "com.olivia.companion" | "com.olivia.companion.mock" = "com.olivia.companion.mock",
): FirebasePublicConfig {
  try {
    const root = value as { project_info: { project_id: string }; client: Array<{
      client_info: { mobilesdk_app_id: string; android_client_info: { package_name: string } };
      api_key: Array<{ current_key: string }>;
    }> };
    if (root.project_info.project_id !== projectId) throw new Error();
    const matches = root.client.filter(c => c.client_info.android_client_info.package_name === packageName);
    if (matches.length !== 1) throw new Error();
    const client = matches[0]!;
    const applicationId = client.client_info.mobilesdk_app_id;
    const apiKey = client.api_key[0]?.current_key;
    if (!/^1:\d+:android:[a-f0-9]+$/i.test(applicationId) || !apiKey || !/^AIza[\w-]{20,}$/.test(apiKey)) throw new Error();
    return { projectId, applicationId, apiKey };
  } catch { throw new InfraConfigError("OLIVIA_FIREBASE_CONFIG_PATH"); }
}
