#!/usr/bin/env bash
# Usage: cold.sh <label> [runs]. Cold launches; reports am TotalTime and the fully drawn delay in ms.
source "$(dirname "$0")/env.sh"
LABEL="$1"; RUNS="${2:-12}"
FILE="$OUT/cold-$LABEL.csv"
echo "# $(conditions)" >> "$FILE"
for i in $(seq 1 "$RUNS"); do
  s "am force-stop $PKG"
  sleep 2
  s "logcat -c" >/dev/null
  total=$(s "am start -W -n $ACT" | awk '/TotalTime/{print $2}')
  sleep 5
  drawn=$(s "logcat -d -s ActivityTaskManager:I | grep 'Fully drawn $PKG' | tail -1" | sed -E 's/.*\+([0-9]+s)?([0-9]+)ms.*/\1 \2/' | awk '{ if (NF==2) { sub("s","",$1); print $1*1000+$2 } else print $1 }')
  echo "$i,$total,$drawn" | tee -a "$FILE"
done
s "am force-stop $PKG"
