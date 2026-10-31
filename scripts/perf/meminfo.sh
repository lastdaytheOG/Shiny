#!/usr/bin/env bash
# Usage: meminfo.sh <label> <minutes> [poll seconds]. Samples PSS, Java and native heap, and GC counts from a running app.
source "$(dirname "$0")/env.sh"
LABEL="$1"; MINS="$2"; POLL="${3:-60}"
FILE="$OUT/mem-$LABEL.csv"
echo "# $(date '+%F %T') $(conditions)" >> "$FILE"
echo "epoch,pss_kb,java_kb,native_kb,graphics_kb,threads,cumulative_janky" >> "$FILE"
END=$(( $(date +%s) + MINS * 60 ))
while [ "$(date +%s)" -lt "$END" ]; do
  pid=$(s "pidof $PKG")
  [ -z "$pid" ] && { echo "$(date +%s),DEAD" >> "$FILE"; sleep "$POLL"; continue; }
  m=$(s "dumpsys meminfo $pid")
  pss=$(echo "$m" | awk '/TOTAL PSS:/{print $3; exit}')
  java=$(echo "$m" | awk '/Java Heap:/{print $3; exit}')
  native=$(echo "$m" | awk '/Native Heap:/{print $3; exit}' )
  graphics=$(echo "$m" | awk '/Graphics:/{print $2; exit}')
  threads=$(s "ls /proc/$pid/task | wc -l")
  janky=$(s "dumpsys gfxinfo $PKG | grep -m1 'Janky frames:'" | awk '{print $3}')
  echo "$(date +%s),$pss,$java,$native,$graphics,$threads,$janky" >> "$FILE"
  sleep "$POLL"
done
