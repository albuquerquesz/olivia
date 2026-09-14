# First delivery - deterministic cut planner

Status: code and unit tests written; no build, test execution, Whisper runtime, media import, or
hardware validation was run.

`CutPlanner` accepts only an already-aligned native-media timeline, validated transcript segments,
and a known recording duration. It suggests exclusion intervals for uncorrected deviations,
degraded capture, and explicit spoken retakes; it never treats missing transcription as silence.
Overlaps merge deterministically, intervals are bounded, and an entire-session cut is rejected.
No media bytes, transcript persistence, rendering, or editor integration was added.

Whisper, media alignment, import, session PROCESSING/READY lifecycle, Docker, Android execution,
and CapCut handoff remain pending.

`LocalPostProcessor` now composes an injected on-device transcript provider with an already
imported recording, the session timeline, verified media alignment, and `CutPlanner`. Missing
alignment returns a partial review with no automatic cuts rather than an invented offset. The
actual Whisper provider, import lifecycle, and Android PROCESSING/READY transition remain
unimplemented.
