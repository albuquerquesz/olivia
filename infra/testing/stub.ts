/** Synthetic responses only. Never echo requests, headers or log bodies. */
export function startStub(port = 8080, hostname = "0.0.0.0") {
  return Bun.serve({
    port, hostname, maxRequestBodySize: 65_536,
    async fetch(request) {
      const path = new URL(request.url).pathname;
      if (request.method === "GET" && path === "/health") return new Response("ready");
      if (request.method !== "POST") return new Response(null, { status: 405 });
      const scenario = path.split("/").at(-1);
      switch (scenario) {
        case "ok": return Response.json({ diagnoses: [{ kind: "FRAMING", severity: "MAJOR", confidence: 0.9, direction: "TURN_RIGHT" }] });
        case "malformed": return new Response("{", { headers: { "Content-Type": "application/json" } });
        case "invalid-enum": return Response.json({ diagnoses: [{ kind: "FACE_IDENTITY", confidence: 0.9 }] });
        case "invalid-confidence": return Response.json({ diagnoses: [{ kind: "LIGHT", confidence: 3 }] });
        case "unauthorized": return new Response(null, { status: 401 });
        case "forbidden": return new Response(null, { status: 403 });
        case "rate-limit": return new Response(null, { status: 429, headers: { "Retry-After": "1" } });
        case "unavailable": return new Response(null, { status: 503 });
        case "timeout": await Bun.sleep(2_000); return Response.json({ diagnoses: [] });
        default: return new Response(null, { status: 404 });
      }
    },
    error() { return new Response(null, { status: 500 }); },
  });
}

if (import.meta.main) startStub();
