#!/usr/bin/env bash
set -euo pipefail

mkdir -p app/build/ci-diagnostics
# A repeatable phone viewport for native review captures; compact/font-scale tests
# still constrain their own content independently.
adb shell wm size 360x800
adb shell wm density 160
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
adb exec-out run-as ru.sportzal.app tar -cf - files/review-screens \
  > app/build/ci-diagnostics/review-screens.tar 2> app/build/ci-diagnostics/screens-error.txt || true
exit "$test_status"
