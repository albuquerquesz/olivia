import { expect, test } from "bun:test";
import { startStub } from "../stub";

test("real HTTP server lifecycle and deterministic provider failures", async () => {
  const server = startStub(0, "127.0.0.1");
  const base = `http://127.0.0.1:${server.port}`;
  try {
    expect((await fetch(`${base}/health`)).status).toBe(200);
    for (const [scenario, status] of Object.entries({ ok: 200, unauthorized: 401, forbidden: 403, "rate-limit": 429, unavailable: 503 })) {
      const response = await fetch(`${base}/scenarios/${scenario}`, { method: "POST", body: "synthetic-only" });
      expect(response.status).toBe(status);
      expect(await response.text()).not.toContain("synthetic-only");
    }
    const malformed = await fetch(`${base}/scenarios/malformed`, { method: "POST" });
    await expect(malformed.json()).rejects.toThrow();
    await expect(fetch(`${base}/scenarios/timeout`, { method: "POST", signal: AbortSignal.timeout(50) })).rejects.toThrow();
  } finally { await server.stop(true); }
  await expect(fetch(`${base}/health`, { signal: AbortSignal.timeout(500) })).rejects.toThrow();
});
