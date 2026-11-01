#!/usr/bin/env bash
# Usage: ab-journeys.sh <data label> <rounds> <iterations> "<test methods>" <build>... Interleaved journeys, cooling between arms.
source "$(dirname "$0")/env.sh"
DATA="$1"; ROUNDS="$2"; ITER="$3"; TESTS="$4"; shift 4
mkdir "$OUT/.lock" 2>/dev/null || { echo "STOP: another measuring run holds $OUT/.lock" >&2; exit 8; }
trap 'rmdir "$OUT/.lock"' EXIT
DEBUG="$(dirname "$0")/../../Shiny-universal-gms-debug-2026-10-31-intelligence.apk"
for round in $(seq 1 "$ROUNDS"); do
  for b in "$@"; do
    wait_cool "${COOL_LIMIT:-385}"
    bash "$(dirname "$0")/swap-data.sh" "$DEBUG" "$OUT/$DATA.db" "$OUT/apks/$b.apk" | tail -1
    s "input keyevent KEYCODE_HOME"
    sleep 3
    s "am start -W -n $ACT" >/dev/null
    sleep 12
    focused | grep -q "$PKG" || echo "NOT FOCUSED: $(focused)"
    s "am force-stop $PKG"
    bash "$(dirname "$0")/journeys.sh" "$DATA-$b-r$round" "$ITER" $TESTS
  done
done
