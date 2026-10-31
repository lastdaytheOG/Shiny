#!/usr/bin/env bash
# Usage: swap-data.sh <debug apk> <song.db> <apk to leave installed>. Only a debuggable build can receive a
# database through run-as, so the debug build goes on first and the measuring build goes back over it.
source "$(dirname "$0")/env.sh"
DEBUG_APK="$1"; DB="$2"; FINAL="$3"
s "am force-stop $PKG"
a install -r -t "$(cygpath -m "$DEBUG_APK")" | tr -d '\r' | grep -E "Success|Failure" || exit 5
a push "$(cygpath -m "$DB")" /data/local/tmp/seed.db >/dev/null
s "run-as $PKG sh -c 'rm -f databases/song.db databases/song.db-wal databases/song.db-shm; cp /data/local/tmp/seed.db databases/song.db'" || exit 7
s "rm -f /data/local/tmp/seed.db"
s "run-as $PKG ls -la databases/song.db"
[ -n "$FINAL" ] && "$(dirname "$0")/install.sh" "$FINAL"
