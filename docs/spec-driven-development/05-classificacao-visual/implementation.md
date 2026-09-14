# First delivery - closed visual analysis schema

Status: code written; no build, automated test, live model, or hardware validation was run.

`VisualAnalysisSchema` accepts only version `1.0` and a list of up to one diagnosis for each
closed class: `FRAMING`, `LIGHT`, and `HORIZON`. It rejects unknown or missing fields, free-form
text, duplicate classes, invalid confidence, incompatible direction, and every magnitude because
the MVP has no verified glasses calibration. Model provenance and timestamps remain app-owned and
will be attached only by a future Firebase adapter when constructing `FrameAnalysis`.

The decoder returns a controlled `Result.failure` for malformed, incomplete, or contradictory
model output. It neither logs nor persists the raw response. Firebase transport, timeout,
backoff, cancellation, analysis-health signaling, live credentials, and quality evaluation are
still pending in BE-03.2 through BE-03.5.
`GeminiFrameAnalyzer` now provides the one-shot core path behind an injected
`GeminiVisualTransport`: 2-second timeout, no retry of the same frame, content-free failure
codes, schema validation, and correct cancellation propagation. Its Firebase-specific transport
is intentionally not implemented because the Firebase dependency is test-only and no development
credentials were supplied. The next frame is the only recovery path after a failure.
