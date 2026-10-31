#!/usr/bin/env bash
# Usage: install.sh <apk>. Installs a measuring build over com.shiny.music.debug, keeps its data, compiles it
# the way a sideloaded release ends up, and verifies by hash that this exact APK is the one installed.
source "$(dirname "$0")/env.sh"
APK="$1"
[ -f "$APK" ] || { echo "no such apk: $APK" >&2; exit 2; }
WIN=$(cygpath -m "$APK")
s "am force-stop $PKG"
a install -r -t "$WIN" | tr -d '\r' | grep -E "Success|Failure" || exit 5
want=$(sha1sum "$APK" | cut -d' ' -f1)
have=$(apk_hash)
[ "$want" = "$have" ] || { echo "STOP: installed $have, expected $want" >&2; exit 6; }
s "cmd package compile --reset $PKG" >/dev/null
s "am start -W -n $ACT" >/dev/null
sleep 6
s "am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE -n $PKG/androidx.profileinstaller.ProfileInstallReceiver" >/dev/null
sleep 2
s "am force-stop $PKG"
s "cmd package compile -f -m speed-profile $PKG" | tail -1
echo "installed ${want:0:10} $(s "pm art dump $PKG | grep -m1 -E 'arm64: \[status'")"
