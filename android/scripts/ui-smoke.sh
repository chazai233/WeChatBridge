#!/usr/bin/env bash
# Pull captures before android-emulator-runner shuts the device down, even on failure.
set -u
adb shell settings put secure show_ime_with_hard_keyboard 1
result=0
./gradlew --no-daemon :app:connectedDebugAndroidTest || result=$?
mkdir -p ../.context/ui-screenshots
adb pull /sdcard/Download/wechatbridge-ui ../.context/ui-screenshots/ || true
exit "$result"