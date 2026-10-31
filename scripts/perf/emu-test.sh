#!/usr/bin/env bash
# Usage: emu-test.sh <test class or class#method> [more]. Builds and runs device tests on the emulator.
export MSYS_NO_PATHCONV=1
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
ADB=/c/Users/Lenovo/AppData/Local/Android/Sdk/platform-tools/adb.exe
A="$ADB -s emulator-5554"
cd "$(dirname "$0")/../.."
./gradlew "-Dorg.gradle.jvmargs=-Xmx6g" -Pkotlin.compiler.execution.strategy=in-process :app:assembleX86_64GmsDebug :app:assembleX86_64GmsDebugAndroidTest 2>&1 | grep -E "^e: |BUILD" | head -20
APP=app/build/outputs/apk/x86_64Gms/debug/app-x86_64-gms-debug.apk
TEST=app/build/outputs/apk/androidTest/x86_64Gms/debug/app-x86_64-gms-debug-androidTest.apk
$A shell pm trim-caches 999G
$A shell pm uninstall -k com.shiny.music.debug >/dev/null
$A shell settings put global sys_storage_threshold_percentage 0
$A shell settings put global sys_storage_threshold_max_bytes 10485760
$A shell settings put global sys_storage_full_threshold_bytes 1048576
$A install -t -g "$(cygpath -m $APP)" | tail -1 | cut -c1-120
for K in sys_storage_threshold_percentage sys_storage_threshold_max_bytes sys_storage_full_threshold_bytes; do $A shell settings delete global $K >/dev/null; done
$A install -r -t "$(cygpath -m $TEST)" | tail -1 | cut -c1-120
[ "$(sha1sum $APP | cut -c1-40)" = "$($A shell 'sha1sum $(pm path com.shiny.music.debug | head -1 | cut -d: -f2)' | cut -c1-40)" ] && echo "app hash ok" || echo "APP HASH MISMATCH"
for T in "$@"; do
  $A logcat -c
  $A shell am instrument -w -e class "com.shiny.music.$T" com.shiny.music.debug.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | grep -E "^Tests run|^OK|^[0-9]+\) |AssertionError|Exception:" | cut -c1-200 | head -30
done
