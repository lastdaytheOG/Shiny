#!/usr/bin/env bash
# Usage: battery-log.sh <label> <minutes> [poll seconds]. Logs the charge counter while the phone runs on battery.
# Refuses to run on a charging phone, because the counter then says nothing about consumption.
source "$(dirname "$0")/env.sh"
LABEL="$1"; MINS="$2"; POLL="${3:-60}"
FILE="$OUT/battery-$LABEL.csv"
status=$(s "dumpsys battery | grep -m1 ' status:'" | awk '{print $2}')
powered=$(s "dumpsys battery | grep -E '(AC|USB|Wireless) powered: true'")
[ "$status" = "3" ] && [ -z "$powered" ] || { echo "STOP: phone is charging or powered (status=$status $powered)" >&2; exit 4; }
echo "# $(date '+%F %T') $(conditions)" >> "$FILE"
END=$(( $(date +%s) + MINS * 60 ))
while [ "$(date +%s)" -lt "$END" ]; do
  now=$(date +%s)
  uah=$(s "cat /sys/class/power_supply/battery/charge_counter")
  ma=$(s "cat /sys/class/power_supply/battery/current_now")
  temp=$(s "cat /sys/class/power_supply/battery/temp")
  echo "$now,$uah,$ma,$temp" >> "$FILE"
  sleep "$POLL"
done
echo "# end $(date '+%F %T') $(conditions)" >> "$FILE"
awk -F, '!/^#/ {if(!f){f=$2;t0=$1} l=$2;t1=$1} END {h=(t1-t0)/3600; printf "used %.0f uAh over %.2f h = %.1f mA average\n", f-l, h, (f-l)/1000/h}' "$FILE"
