import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const outputDirectory = join(
  process.cwd(),
  "apps/android/app/src/main/res/raw",
);
mkdirSync(outputDirectory, { recursive: true });

const sampleRate = 16_000;
const durationMs = 80;
const channels = 2;
const bitsPerSample = 16;
const frameCount = sampleRate * durationMs / 1_000;

const cues = {
  cue_direction_left: { frequency: 520, left: 0.95, right: 0.16 },
  cue_direction_right: { frequency: 520, left: 0.16, right: 0.95 },
  cue_direction_neutral: { frequency: 680, left: 0.72, right: 0.72 },
  cue_session_started: { frequency: 880, left: 0.72, right: 0.72 },
  cue_session_paused: { frequency: 560, left: 0.72, right: 0.72 },
  cue_session_resumed: { frequency: 760, left: 0.72, right: 0.72 },
  cue_session_stopped: { frequency: 440, left: 0.72, right: 0.72 },
  cue_connection_lost: { frequency: 220, left: 0.72, right: 0.72 },
  cue_connection_restored: { frequency: 660, left: 0.72, right: 0.72 },
  cue_voice_command_error: { frequency: 300, left: 0.72, right: 0.72 },
  cue_voice_input_unavailable: { frequency: 180, left: 0.72, right: 0.72 },
};

for (const [name, cue] of Object.entries(cues)) {
  const dataSize = frameCount * channels * (bitsPerSample / 8);
  const wav = Buffer.alloc(44 + dataSize);
  wav.write("RIFF", 0, "ascii");
  wav.writeUInt32LE(36 + dataSize, 4);
  wav.write("WAVE", 8, "ascii");
  wav.write("fmt ", 12, "ascii");
  wav.writeUInt32LE(16, 16);
  wav.writeUInt16LE(1, 20);
  wav.writeUInt16LE(channels, 22);
  wav.writeUInt32LE(sampleRate, 24);
  wav.writeUInt32LE(sampleRate * channels * bitsPerSample / 8, 28);
  wav.writeUInt16LE(channels * bitsPerSample / 8, 32);
  wav.writeUInt16LE(bitsPerSample, 34);
  wav.write("data", 36, "ascii");
  wav.writeUInt32LE(dataSize, 40);

  for (let frame = 0; frame < frameCount; frame += 1) {
    const fadeFrames = sampleRate * 8 / 1_000;
    const envelope = Math.min(
      1,
      (frame + 1) / fadeFrames,
      (frameCount - frame) / fadeFrames,
    );
    const sample = Math.sin(2 * Math.PI * cue.frequency * frame / sampleRate)
      * envelope
      * 0.45
      * 32_767;
    const offset = 44 + frame * channels * 2;
    wav.writeInt16LE(Math.round(sample * cue.left), offset);
    wav.writeInt16LE(Math.round(sample * cue.right), offset + 2);
  }

  writeFileSync(join(outputDirectory, `${name}.wav`), wav);
}
