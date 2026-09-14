# Olivia - pre-hardware and hardware-day runbook

Purpose: make the in-person Ray-Ban/DAT session an adapter and evidence exercise, not a product
development session. The mock remains the labelled fallback; it is not evidence of hardware.

## Verified locally on 2026-09-13

- `:core:test`: passed.
- `:app:assembleMockDebug`: passed.
- APK: `apps/android/app/build/outputs/apk/mock/debug/app-mock-debug.apk`.
- Emulator install did not run because no ADB device was connected.

The checked path is offline and uses the synthetic `GlassesGateway`, synthetic visual fixtures,
local timeline, Android audio adapter boundary, and session watchdog. It does not validate DAT,
Bluetooth, Gemini, Porcupine, native recording, Whisper, audio at the glasses, or battery.

## Before the venue

1. Keep the mock APK and Android SDK/AVD available locally. Re-run the core suite and mock build
   after code changes.
2. Keep secrets outside Git: DAT `GITHUB_TOKEN`, Wearables Developer Center registration values,
   Firebase development config/App Check, and Porcupine access key/models.
3. Do not add `mwdat-display`; Olivia uses audio, not a glasses UI.
4. Preserve `GlassesGateway`, `FrameAnalyzer`, `AudioFeedback`, `RecordingSource`, and
   `LocalCommandRecognizer` boundaries. Hardware adapters replace only their implementation.
5. Prepare a labelled mock recording/demo as fallback, with no claim that it came from the glasses.

## Venue order

1. Confirm Android device, app registration, DAT credentials, and DAT opt-outs.
2. Run MockDeviceKit smoke: connect, emit one 720x1280 frame, pause/resume, unavailable/recovered.
3. Replace only `MockGlassesGateway` with the DAT adapter. Confirm a real frame origin before
   continuing; never relabel mock frames as DAT.
4. Verify one healthy live loop: frame -> visual result -> audio callback -> later-frame outcome ->
   timeline. Measure it; do not reuse mock latency as a physical number.
5. Independently verify native start/stop, simultaneous streaming, Bluetooth-loss preservation,
   and post-STOPPED import. A streaming session does not prove native recording.
6. Verify the selected Android media route reaches Ray-Ban speakers. TTS callback alone is not an
   acoustic proof.
7. Record actual device, firmware, model/config, network, battery start/end, failures, and the
   limits still open. No frame, raw audio, transcript, credential, or full private URI belongs in
   the evidence report.

## Stop conditions and fallback

- Missing DAT package/authentication: remain in labelled mock mode; do not patch the domain loop.
- No real frame or wrong format/orientation: stop visual claims and diagnose the adapter.
- Audio not audible in glasses: retain timeline evidence but state that audio hardware proof failed.
- Native recording unavailable: do not reconstruct a final video from the Bluetooth frame stream.
- Firebase/Porcupine unavailable: use mock only and state that the real service/voice gate remains
  open.

The full evidence matrix remains in `docs/spec-driven-development/10-medicao-e-evidencias`.
