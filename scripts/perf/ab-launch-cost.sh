#!/usr/bin/env bash
# Usage: ab-launch-cost.sh <data label> <rounds> <runs> <seconds> <build>... Process CPU time and memory just after a cold start, interleaved.
source "$(dirname "$0")/env.sh"
DATA="$1"; ROUNDS="$2"; RUNS="$3"; SECS="$4"; shift 4
mkdir "$OUT/.lock" 2>/dev/null || { echo "STOP: another measuring run holds $OUT/.lock" >&2; exit 8; }
trap 'rmdir "$OUT/.lock"' EXIT
DEBUG="$(dirname "$0")/../../Shiny-universal-gms-debug-2026-10-31-intelligence.apk"
for round in $(seq 1 "$ROUNDS"); do
  for b in "$@"; do
    wait_cool "${COOL_LIMIT:-370}"
    bash "$(dirname "$0")/swap-data.sh" "$DEBUG" "$OUT/$DATA.db" "$OUT/apks/$b.apk" | tail -1
    s "input keyevent KEYCODE_HOME"
    sleep 3
    s "am start -W -n $ACT" >/dev/null
    sleep 12
    s "am force-stop $PKG"
    for i in $(seq 1 "$RUNS"); do
      sleep 3
      s "am start -W -n $ACT" >/dev/null
      is_ours || echo "NOT FOCUSED: $(focused)"
      sleep "$SECS"
      pid=$(s "pidof $PKG")
      total=$(s "cat /proc/$pid/stat" | awk '{print "cpu_jiffies=" $14 + $15, "threads=" $20}')
      pss=$(s "dumpsys meminfo $pid" | awk '/TOTAL PSS:/{print $3; exit}')
      s "am force-stop $PKG"
      echo "$(date +%H:%M:%S) $DATA $b round $round run $i: $total pss_kb=$pss" | tee -a "$OUT/launch-cost-$DATA-$b.txt"
    done
    echo "$(conditions)"
  done
done
