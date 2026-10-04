#!/usr/bin/env bash
# Cold-restart trace for artwork and resume (PERF_HANDOFF.md section 7). Measurement only: drives
# the debug app through intents, taps and media keys, and reads its caches with run-as.
#
#   scripts/perf-cold-trace.sh seed         play the seed song long enough to cache audio, pause
#   scripts/perf-cold-trace.sh launch       force-stop, cold launch, timed screenshots, Coil journal
#   scripts/perf-cold-trace.sh play X Y     tap (X,Y) — the mini player's play button — log until
#                                           audible, diff the player cache; no X Y sends a media key
#
# Before tracing a fresh debug install, pre-verify it or launch timing is all DEX verification:
#   adb shell cmd package compile -m verify -f com.shiny.music.debug
# Find the mini player's play button with `adb shell uiautomator dump`: the bar above the tab bar
# holding exactly two clickable children; play/pause is the left one.
export MSYS_NO_PATHCONV=1
ADB="${ADB:-${ANDROID_HOME:-/c/Users/Lenovo/AppData/Local/Android/Sdk}/platform-tools/adb.exe}"
P="${PKG:-com.shiny.music.debug}"
A=$P/com.shiny.music.MainActivity
OUT="${OUT:-$(dirname "$0")/../baselineprofile/build/perf-cold-trace}"
SEED="${SEED:-Rr1Cdli5nE8}"
mkdir -p "$OUT"

rs() { $ADB shell "run-as $P sh -c '$1'"; }
uptime_ms() { $ADB shell "awk '{printf \"%d\", \$1*1000}' /proc/uptime"; }
playing() { $ADB shell dumpsys media_session | grep -qE "state=(PLAYING|3)[,)(]"; }
wait_playing() { # $1 = timeout s
  local end=$(( $(date +%s) + $1 ))
  while [ "$(date +%s)" -lt "$end" ]; do playing && return 0; done
  return 1
}
# One line per player-cache span: size, date, time, path. Span names start with the cache id of the
# resource, so a resource that was removed and re-downloaded shows up under a new id.
exo_snapshot() { rs "find files/exoplayer -type f -name \"*.exo\" -exec ls -l {} \\; | awk \"{print \\\$5, \\\$6, \\\$7, \\\$8}\"" | tr -d '\r' | sort; }

case "$1" in
seed)
  $ADB shell am force-stop $P
  $ADB shell am start -W -a android.intent.action.VIEW -d "https://music.youtube.com/watch?v=$SEED" -n $A >/dev/null
  wait_playing 90 && echo "seed playing" || { echo "seed never played"; exit 1; }
  sleep 25                                   # let the player cache well past the first chunk
  $ADB shell input keyevent 127              # pause
  sleep 5                                    # queue persistence debounce
  echo "seeded; player cache spans: $(rs 'find files/exoplayer -name "*.exo" | wc -l')"
  ;;
launch)
  $ADB shell am force-stop $P; sleep 2
  rs 'wc -l < cache/coil/journal' | tr -d '\r' > "$OUT/journal-lines-before.txt"
  exo_snapshot > "$OUT/exo-before-launch.txt"
  $ADB logcat -c
  # Device-side loop, so screenshot times are measured on the device from the start intent.
  $ADB shell 't0=$(awk "{printf \"%d\", \$1*1000}" /proc/uptime); am start -n '"$A"' >/dev/null;
    for i in $(seq 1 18); do
      t=$(awk "{printf \"%d\", \$1*1000}" /proc/uptime); screencap -p /sdcard/cold_$i.png; echo "shot $i at $((t - t0)) ms";
    done' | tee "$OUT/shots.txt"
  sleep 3
  $ADB logcat -d > "$OUT/logcat-launch.txt"
  for i in $(seq 1 18); do $ADB pull /sdcard/cold_$i.png "$OUT/cold_$i.png" >/dev/null 2>&1; done
  echo "--- launch milestones:"
  grep -E "Displayed $P|Player successfully initialized|Google Cast initialized|PlayerConnection created|Skipped [0-9]+ frames|WaitForGcToComplete" "$OUT/logcat-launch.txt" | cut -c1-170
  # Coil appends READ lines to its journal lazily; read them a few seconds after launch.
  sleep 10
  n0=$(cat "$OUT/journal-lines-before.txt")
  echo "--- Coil journal since launch (READ = disk hit, DIRTY/CLEAN = downloaded, REMOVE = evicted):"
  rs "tail -n +$((n0 + 1)) cache/coil/journal" | tr -d '\r' | tee "$OUT/journal-during-launch.txt" | cut -d' ' -f1 | sort | uniq -c
  echo "screenshots in $OUT"
  ;;
play)
  exo_snapshot > "$OUT/exo-before-play.txt"
  $ADB logcat -c
  t0=$(uptime_ms)
  if [ -n "$2" ]; then
    $ADB shell input tap "$2" "$3"
  else
    $ADB shell input keyevent 126
  fi
  if wait_playing 60; then echo "playing after ~$(( $(uptime_ms) - t0 )) ms (media session state, polled over adb: coarse)"; else echo "never played"; fi
  sleep 3
  $ADB shell input keyevent 127
  sleep 2
  $ADB logcat -d > "$OUT/logcat-play.txt"
  exo_snapshot > "$OUT/exo-after-play.txt"
  echo "--- player cache spans: before $(wc -l < "$OUT/exo-before-play.txt"), after $(wc -l < "$OUT/exo-after-play.txt")"
  echo "--- deleted during play:"; comm -23 "$OUT/exo-before-play.txt" "$OUT/exo-after-play.txt"
  echo "--- written during play:"; comm -13 "$OUT/exo-before-play.txt" "$OUT/exo-after-play.txt"
  echo "--- resolver and cache:"
  grep -E "Ghost cache|Cached audio kept|FETCHING STREAM|BYPASSING CACHE|Preloading stream|resolve\.(begin|sts\.stored|sts\.extract|success)|client\.probe\.early|mainClient\.request\.done" "$OUT/logcat-play.txt" | cut -c1-200
  ;;
*) echo "usage: $0 seed | launch | play [X Y]"; exit 2 ;;
esac
