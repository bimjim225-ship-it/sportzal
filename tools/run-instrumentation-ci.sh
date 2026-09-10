#!/usr/bin/env bash
set -euo pipefail

mkdir -p app/build/ci-diagnostics
{
  adb shell wm size
  adb shell wm density
  adb shell settings get system font_scale
} > app/build/ci-diagnostics/device.txt 2>&1 || true

adb logcat -c || true

test_status=0
./gradlew connectedDebugAndroidTest || test_status=$?

# Снять logcat до остановки эмулятора android-emulator-runner.
adb logcat -d -v threadtime > app/build/ci-diagnostics/logcat.txt 2>&1 || true
exit "$test_status"
