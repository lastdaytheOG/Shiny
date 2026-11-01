#!/usr/bin/env bash
# Usage: ab-cold.sh <data label> <rounds> <runs> <build>... Interleaved cold starts, cooling between arms.
source "$(dirname "$0")/env.sh"
DATA="$1"; ROUNDS="$2"; RUNS="$3"; shift 3
mkdir "$OUT/.lock" 2>/dev/null || { echo "STOP: another measuring run holds $OUT/.lock" >&2; exit 8; }
trap 'rmdir "$OUT/.lock"' EXIT
DEBUG="$(dirname "$0")/../../Shiny-universal-gms-debug-2026-10-31-intelligence.apk"
for round in $(seq 1 "$ROUNDS"); do
  for b in "$@"; do
    wait_cool "${COOL_LIMIT:-385}"
    bash "$(dirname "$0")/swap-data.sh" "$DEBUG" "$OUT/$DATA.db" "$OUT/apks/$b.apk" | tail -1
    sleep 5
    s "am start -W -n $ACT" >/dev/null
    sleep 12
    focused | grep -q "$PKG" || echo "NOT FOCUSED: $(focused)"
    bash "$(dirname "$0")/cold.sh" "$DATA-$b" "$RUNS" >/dev/null
    echo "$(date +%H:%M:%S) done $DATA $b round $round: $(conditions)"
  done
done
python - "$(cygpath -m "$OUT")" "$DATA" "$@" <<'EOF'
import statistics as st, sys
out, data, *builds = sys.argv[1:]
for b in builds:
    t, d = [], []
    for l in open(f'{out}/cold-{data}-{b}.csv'):
        p = l.strip().split(',')
        if l.startswith('#') or len(p) != 3 or not p[1] or not p[2] or p[1] == '0':
            continue
        t.append(int(p[1])); d.append(int(p[2]))
    q = lambda v, f: sorted(v)[int(len(v) * f)]
    print(f"{data} {b} n={len(t)} first frame med {st.median(t)} p90 {q(t, .9)} max {max(t)} | home ready med {st.median(d)} p90 {q(d, .9)} max {max(d)}")
EOF
