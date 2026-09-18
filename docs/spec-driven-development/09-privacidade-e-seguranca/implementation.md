# First delivery - local data inventory

Status: inventory written; no Android sandbox inspection, provider review, integration test, or
hardware validation was run.

| Data | Owner and location | Lifetime | Prohibited destinations |
| --- | --- | --- | --- |
| Live frame bytes | `GlassesGateway` -> sampler -> analyzer memory | One callback / analysis attempt | Timeline, metrics, filesystem, logs |
| Visual-model response | `GeminiVisualTransport` / schema stack memory | One validation attempt | Timeline, metrics, filesystem, logs |
| Command microphone PCM | future local Porcupine adapter memory | One recognizer callback | Firebase, timeline, filesystem, logs |
| Timeline metadata | `FileTimeline` app-private directory | Until user-managed deletion | Public storage, analytics |
| Native recording | glasses then explicit local import | Creator-controlled post-take asset | Live-loop pipeline |
| Aggregate loop metrics | `LiveLoopMetrics` memory | Bounded current process samples | Analytics upload, filesystem by default |

Existing enforcement points are the `Frame` contract, strict timeline schema, content-free visual
analysis failures, and bounded metrics. `GeminiVisualTransport` makes any future provider adapter
responsible for keeping its input/output in memory. The mock recognizer does not request a
microphone and the mock camera uses no image payload.

Before any real-media run, BE-10.3 through BE-10.5 still require Firebase data-treatment review,
Android backup/sandbox inspection, cleanup/cancellation evidence, and an authorized sharing and
consent checklist. Local app discard does not establish provider-side retention behavior.
