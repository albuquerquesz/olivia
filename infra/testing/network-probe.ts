/** Cross-container HTTP integration. This is not a Firebase SDK emulator. */
const base = process.env.OLIVIA_STUB_URL;
if (base !== "http://provider-stub:8080") throw new Error("INVALID_STUB_URL");
for (const [scenario, status] of Object.entries({ ok: 200, unauthorized: 401, forbidden: 403, "rate-limit": 429, unavailable: 503 })) {
  const response = await fetch(`${base}/scenarios/${scenario}`, { method: "POST", signal: AbortSignal.timeout(2000) });
  if (response.status !== status) throw new Error("UNEXPECTED_STATUS");
}
let timedOut = false;
try { await fetch(`${base}/scenarios/timeout`, { method: "POST", signal: AbortSignal.timeout(50) }); }
catch (error) { timedOut = error instanceof Error && error.name === "TimeoutError"; }
if (!timedOut) throw new Error("TIMEOUT_NOT_OBSERVED");
