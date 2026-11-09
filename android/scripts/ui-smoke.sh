#!/usr/bin/env bash
# Pull captures before android-emulator-runner shuts the device down, even on failure.
set -u
result=0
./gradlew --no-daemon :app:connectedDebugAndroidTest || result=$?
mkdir -p ../.context/ui-screenshots
adb pull /sdcard/Download/wechatbridge-ui ../.context/ui-screenshots/ || true
exit "$result"