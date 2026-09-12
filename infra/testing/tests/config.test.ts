import { describe, expect, test } from "bun:test";
import { childEnvironment, firebasePublicConfig, readInfraConfig, Secret } from "../../../packages/env/src/infra";
import { TestClock } from "../clock";

describe("BE-13 configuration boundaries", () => {
  test("offline needs no database or credentials", () => {
    expect(readInfraConfig({ FIREBASE_APP_CHECK_DEBUG_TOKEN: "never-forward" })).toEqual({ mode: "offline" });
    expect(childEnvironment({ PATH: "/bin", DATABASE_URL: "secret", GEMINI_API_KEY: "secret", FIREBASE_APP_CHECK_DEBUG_TOKEN: "secret" })).toEqual({ PATH: "/bin" });
  });
  test("unknown mode and incomplete live modes fail closed", () => {
    for (const mode of ["typo", "live-ai", "whisper"]) {
      expect(() => readInfraConfig({ OLIVIA_TEST_MODE: mode })).toThrow();
    }
  });
  test("secrets cannot accidentally serialize", () => {
    const secret = new Secret("sensitive");
    expect(JSON.stringify({ secret })).not.toContain("sensitive");
    expect(String(secret)).toBe("[REDACTED]");
    expect(secret.reveal()).toBe("sensitive");
  });
  test("live budget, model and required credentials", () => {
    const env = {
      OLIVIA_TEST_MODE: "live-ai", OLIVIA_FIREBASE_PROJECT_ID: "olivia-dev",
      OLIVIA_AI_MODEL: "gemini-3.5-flash-lite", OLIVIA_LIVE_AI_MAX_REQUESTS: "3",
      OLIVIA_LIVE_AI_ESTIMATED_COST_PER_REQUEST_USD: "0.01", OLIVIA_LIVE_AI_MAX_ESTIMATED_COST_USD: "0.03",
      OLIVIA_FIREBASE_CONFIG_PATH: "fixture.json", FIREBASE_APP_CHECK_DEBUG_TOKEN: "synthetic",
    };
    expect(readInfraConfig(env).mode).toBe("live-ai");
    for (const patch of [
      { OLIVIA_LIVE_AI_MAX_ESTIMATED_COST_USD: "0.02" },
      { OLIVIA_LIVE_AI_MAX_REQUESTS: "13" }, { OLIVIA_LIVE_AI_MAX_REQUESTS: "3.5" },
      { OLIVIA_AI_MODEL: "unapproved" }, { FIREBASE_APP_CHECK_DEBUG_TOKEN: "" },
    ]) expect(() => readInfraConfig({ ...env, ...patch })).toThrow();
  });
  test("Firebase config belongs to selected dev project and mock package", () => {
    const value = { project_info: { project_id: "olivia-dev" }, client: [{
      client_info: { mobilesdk_app_id: "1:123:android:abcdef", android_client_info: { package_name: "com.olivia.companion.mock" } },
      api_key: [{ current_key: "AIza" + "x".repeat(35) }],
    }] };
    expect(firebasePublicConfig(value, "olivia-dev").applicationId).toBe("1:123:android:abcdef");
    expect(() => firebasePublicConfig(value, "different-project")).toThrow();
    expect(() => firebasePublicConfig({}, "olivia-dev")).toThrow();
  });
  test("Whisper requires verified model and fixture", () => {
    const env = { OLIVIA_TEST_MODE: "whisper", OLIVIA_WHISPER_MODEL_PATH: "model.bin",
      OLIVIA_WHISPER_MODEL_SHA256: "a".repeat(64), OLIVIA_WHISPER_FIXTURE_PATH: "sample.wav",
      OLIVIA_WHISPER_FIXTURE_SHA256: "b".repeat(64), OLIVIA_WHISPER_EXPECTED_TEXT: "synthetic speech" };
    expect(readInfraConfig(env).mode).toBe("whisper");
    expect(() => readInfraConfig({ ...env, OLIVIA_WHISPER_MODEL_SHA256: "wrong" })).toThrow();
  });
  test("virtual clock is monotonic", () => {
    const clock = new TestClock();
    clock.advance(4000);
    expect(clock.now()).toBe(4000);
    expect(() => clock.advance(-1)).toThrow();
  });
});
