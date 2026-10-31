#!/usr/bin/env bash
# Installs the emulator debug build, working around its nearly full /data; keeps app data; checks the hash.
export MSYS_NO_PATHCONV=1
ADB=/c/Users/Lenovo/AppData/Local/Android/Sdk/platform-tools/adb.exe
A="$ADB -s emulator-5554"
APP="${1:-app/build/outputs/apk/x86_64Gms/debug/app-x86_64-gms-debug.apk}"
$A shell pm trim-caches 999G
$A shell pm uninstall -k com.shiny.music.debug >/dev/null
$A shell settings put global sys_storage_threshold_percentage 0
$A shell settings put global sys_storage_threshold_max_bytes 10485760
$A shell settings put global sys_storage_full_threshold_bytes 1048576
$A install -t -g "$(cygpath -m "$APP")" | tail -1 | cut -c1-120
for K in sys_storage_threshold_percentage sys_storage_threshold_max_bytes sys_storage_full_threshold_bytes; do $A shell settings delete global $K >/dev/null; done
[ "$(sha1sum "$APP" | cut -c1-40)" = "$($A shell 'sha1sum $(pm path com.shiny.music.debug | head -1 | cut -d: -f2)' | cut -c1-40)" ] && echo "app hash ok" || { echo "APP HASH MISMATCH"; exit 1; }
