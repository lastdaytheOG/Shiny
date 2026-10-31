#!/usr/bin/env bash
# Usage: cycles.sh <label> [seconds]. App CPU cycles per thread over a window, in whatever state the app is in.
source "$(dirname "$0")/env.sh"
LABEL="$1"; SECS="${2:-30}"
SP=/c/Users/Lenovo/AppData/Local/Android/Sdk/ndk/28.2.13676358/simpleperf/bin/android/arm64/simpleperf
s "ls /data/local/tmp/simpleperf" >/dev/null 2>&1 || a push "$(cygpath -w "$SP")" /data/local/tmp/simpleperf >/dev/null
s "chmod 755 /data/local/tmp/simpleperf"
FILE="$OUT/cycles-$LABEL.txt"
{
  echo "# $(date '+%F %T') $(conditions)"
  s "/data/local/tmp/simpleperf stat --app $PKG -e cpu-cycles,instructions,context-switches,page-faults --per-thread --duration $SECS --csv -o /data/local/tmp/stat.csv" 2>&1 | tail -2
  s "cat /data/local/tmp/stat.csv"
} | tee -a "$FILE" | awk -F, '
  /^#/ {print; next}
  $0 ~ /cpu-cycles/ {c[$1","$2]+=$3; t+=$3}
  $0 ~ /context-switches/ {w[$1","$2]+=$3; wt+=$3}
  END {printf "total_cycles=%d total_switches=%d\n", t, wt; n=asorti(c,k); for(i=1;i<=n;i++) printf "%s\t%d\t%d\n", k[i], c[k[i]], w[k[i]]}'
