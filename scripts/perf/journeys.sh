#!/usr/bin/env bash
# Usage: journeys.sh <label> <iterations> <test method>... Runs JourneyBenchmarks on the phone and keeps the JSON results.
source "$(dirname "$0")/env.sh"
LABEL="$1"; ITER="$2"; shift 2
DIR="$OUT/journeys/$LABEL"; mkdir -p "$DIR"
MEDIA=/sdcard/Android/media/com.shiny.music.baselineprofile
for T in "$@"; do
  s "rm -rf $MEDIA/*" >/dev/null
  echo "--- $LABEL $T $(conditions)"
  a shell "am instrument -w -r -e class com.shiny.music.baselineprofile.JourneyBenchmarks#$T -e targetAppId $PKG -e shiny.iterations $ITER -e androidx.benchmark.suppressErrors DEBUGGABLE,LOW-BATTERY,EMULATOR,UNLOCKED com.shiny.music.baselineprofile/androidx.test.runner.AndroidJUnitRunner" | tr -d '\r' > "$DIR/$T.log"
  grep -E "^OK|^FAILURES|^Tests run|INSTRUMENTATION_FAILED|Process crashed|shortMsg" "$DIR/$T.log" | cut -c1-160
  f=$(s "ls $MEDIA | grep -E 'benchmarkData.json$' | head -1")
  [ -n "$f" ] && a pull "$MEDIA/$f" "$(cygpath -m "$DIR")/$T.json" >/dev/null
done
s "am force-stop $PKG"
