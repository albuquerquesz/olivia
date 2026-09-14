# First delivery - fixture recording source and media alignment

Status: code and unit tests written; no build, test execution, DAT transfer, native recording, or
hardware validation was run.

`FixtureRecordingSource` models a completed local fixture transfer using metadata only. It does
not carry bytes, upload media, use a network URL, or make any statement about DAT capabilities.
`LinearMediaAlignment` maps a session timeline only when an externally verified linear anchor has
uncertainty at or below 250 ms; otherwise it returns no media position and automatic cuts must
remain blocked. A real recording with pauses or restarts needs a segment map and is not covered by
this first linear contract.

Native start/stop, simultaneous streaming, Bluetooth-loss preservation, actual import, MIME and
URI validation, and physical evidence remain open in BE-12 and INT-06.
