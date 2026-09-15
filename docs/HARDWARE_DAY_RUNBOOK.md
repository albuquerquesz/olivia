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

1. Confirm Android device, app registration, DAT credentials, DAT opt-outs, and the selected
   Bluetooth audio route. Before changing product code, run the documented A/B experiment with
   real workshop noise: phone microphone + A2DP media versus glasses microphone + HFP. Record
   recognition success, audible output, route format, and the two-second HFP setup delay. HFP is
   an Android/Bluetooth experiment, not an assumed DAT capability; the submitted phone-local
   command route remains the fallback unless the team explicitly approves a product-contract change.
2. Prove native recording before building refinements: start/stop method, coexistence with DAT
   streaming, import through Meta AI/gallery, and a sync marker (tone or clap) usable to align
   the stream clock with native media. Mark 3K as roadmap if any of these steps fails; do not
   relabel the Bluetooth stream as 3K output.
3. Run MockDeviceKit smoke: connect, emit one 720x1280 frame, pause/resume, unavailable/recovered.
4. Replace only `MockGlassesGateway` with the DAT adapter. Confirm a real frame origin before
   continuing; never relabel mock frames as DAT.
5. Configure and compare the lowest DAT stream setting that retains usable framing (360x640,
   504x896, or the documented minimum) at 2 fps; Olivia still analyzes only one frame every two
   seconds. Record the actual delivered format rather than assuming a requested value.
6. Verify one healthy live loop: frame -> visual result -> audio callback -> later-frame outcome ->
   timeline. The engine now waits for two matching sampled analyses before it speaks. Measure p50
   and worst case; do not reuse mock latency as a physical number.
7. Verify the selected Android media route reaches Ray-Ban speakers. TTS callback alone is not an
   acoustic proof.
8. Record actual device, firmware, model/config, network, battery start/end, failures, and the
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
