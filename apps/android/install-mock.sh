#!/bin/sh
# Installs the mock debug APK. On signature mismatch, replaces the leftover package.
set -eu
root=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
apk=$root/app/build/outputs/apk/mock/debug/app-mock-debug.apk
pkg=com.olivia.companion.mock
activity=$pkg/com.olivia.companion.MainActivity

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found; APK is at $apk" >&2
  exit 0
fi
if ! adb devices 2>/dev/null | awk 'NR>1 && $2=="device" {found=1} END {exit !found}'; then
  echo "No Android device; APK is at $apk" >&2
  exit 0
fi

if ! adb install -r -t "$apk"; then
  adb uninstall "$pkg" >/dev/null 2>&1 || true
  adb install -r -t "$apk"
fi
adb shell am start -n "$activity" >/dev/null 2>&1 || true
