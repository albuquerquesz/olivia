# First delivery - fixture recording source and media alignment

Status: fixture source, alignment contract, Android local-import adapter, and an ephemeral live
stream preview are implemented. Unit/build verification is covered below; DAT transfer, native
recording, and hardware validation remain open.

`FixtureRecordingSource` models a completed local fixture transfer using metadata only. It does
not carry bytes, upload media, use a network URL, or make any statement about DAT capabilities.
`LinearMediaAlignment` maps a session timeline only when an externally verified linear anchor has
uncertainty at or below 250 ms; otherwise it returns no media position and automatic cuts must
remain blocked. A real recording with pauses or restarts needs a segment map and is not covered by
this first linear contract.

`AndroidLocalRecordingSource` receives a `SessionSnapshot` already in `STOPPED` and registers only
a creator-selected `content://` video after checking declared MIME, readable size, duration, video
track and audio track. Its `fetch` copies the media
to Olivia's private cache with monotonic byte progress; cancellation and failures remove only the
partial private copy, and `discardImportedCopy` never touches the original. This is an explicit
post-STOP fallback for review, not a claim of DAT transfer or of native capture capabilities.

The Sessions screen uses Android's explicit `video/*` picker only when the current runtime snapshot
is `STOPPED`. It presents local validation/import status and never opens the picker while a take is
live, paused, degraded, or unavailable.

Native start/stop, simultaneous streaming, Bluetooth-loss preservation, physical evidence, and
Android execution remain open in BE-12 and INT-06.

The recording screen now mirrors the latest frame received by the DAT/MockDeviceKit gateway when
available, with local red direction overlays. This is a bounded in-memory preview of the 720x1280
direction stream only; it is cleared on pause/stop/close, is never persisted, and does not claim
that a native 3K take was recorded or transferred. The native take gate and the explicit post-STOP
import fallback remain unchanged.

Verification: `:app:testGlassesDebugUnitTest`, `:app:connectedGlassesDebugAndroidTest` with
`MWDAT_USE_MOCK_DEVICE_KIT=true`, and the mock Android instrumentation suite passed on the Moto
G60 on 17/09/2026. `DatMockDeviceKitIntegrationTest` asserts that the runtime publishes a portrait
JPEG preview and clears it after STOP. This closes the MockDeviceKit/preview evidence only; native
DAT transfer, 3K recording, Bluetooth-loss continuity and physical audio remain open.
