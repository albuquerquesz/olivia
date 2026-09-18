"""BE-14 CPU integration, offline at runtime; never prints media or transcript."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile


def verify(path, expected):
    if not re.fullmatch(r"[a-f0-9]{64}", expected or ""):
        raise ValueError("INVALID_HASH")
    digest = hashlib.sha256()
    with open(path, "rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    if digest.hexdigest() != expected:
        raise ValueError("HASH_MISMATCH")


def normalize(text):
    return " ".join(re.findall(r"\w+", text.casefold()))


def check_transcript(value, expected):
    segments = value.get("transcription", [])
    if not segments or not normalize(expected):
        raise ValueError("EMPTY_TRANSCRIPT")
    previous = 0
    for segment in segments:
        start, end = segment["offsets"]["from"], segment["offsets"]["to"]
        if not isinstance(start, int) or not isinstance(end, int) or start < previous or end <= start:
            raise ValueError("INVALID_TIMESTAMPS")
        if not isinstance(segment["text"], str):
            raise ValueError("INVALID_TEXT")
        previous = end
    actual = normalize(" ".join(segment["text"] for segment in segments))
    if normalize(expected) not in actual:
        raise ValueError("TRANSCRIPT_MISMATCH")


def run():
    verify("/inputs/model.bin", os.environ.get("OLIVIA_WHISPER_MODEL_SHA256"))
    verify("/inputs/fixture", os.environ.get("OLIVIA_WHISPER_FIXTURE_SHA256"))
    expected = os.environ["OLIVIA_WHISPER_EXPECTED_TEXT"]
    # Both extracted PCM and generated JSON disappear on every exit path.
    with tempfile.TemporaryDirectory(prefix="olivia-whisper-") as temporary:
        wav = str(Path(temporary) / "audio.wav")
        result = str(Path(temporary) / "transcript")
        subprocess.run(["ffmpeg", "-nostdin", "-v", "error", "-i", "/inputs/fixture", "-t", "60", "-ar", "16000", "-ac", "1", "-c:a", "pcm_s16le", wav], check=True, timeout=60, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        subprocess.run(["whisper-cli", "-m", "/inputs/model.bin", "-f", wav, "-l", "pt", "-t", "2", "-oj", "-of", result], check=True, timeout=300, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        with open(result + ".json", encoding="utf-8") as source:
            check_transcript(json.load(source), expected)


if __name__ == "__main__":
    try:
        run()
    except Exception:
        print('{"suite":"whisper","status":"failed"}')
        sys.exit(1)
    print('{"suite":"whisper","status":"passed"}')
