# First delivery - live loop metrics

Status: code and unit tests written; no build, test execution, hardware measurement, or battery
measurement was run.

`LiveLoopMetrics` stores a bounded, in-memory collection of monotonic timing pairs plus sampling
counters. It computes nearest-rank p50/p95 only from a frame and the matching Android audio-start
callback. Audio request is deliberately different from callback, and neither is evidence of the
acoustic instant at the Ray-Ban speaker. No frame bytes, raw model output, audio, text, or
credential is collected.

The mock runtime records receive/select/discard counts and pairs each selected frame with analysis
and, when present, the matching audio callback. The artifact has no analytics upload or
persistence. BE-07.2, device measurements, battery methods, and every physical evidence gate
remain pending.
