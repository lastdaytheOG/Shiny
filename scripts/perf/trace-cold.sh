#!/usr/bin/env bash
# Usage: trace-cold.sh <label> [seconds]. Perfetto capture of one cold launch; pulls the trace to _local/perf.
source "$(dirname "$0")/env.sh"
LABEL="$1"; SECS="${2:-10}"
CFG=$(cat <<EOF
buffers { size_kb: 131072 fill_policy: RING_BUFFER }
data_sources { config { name: "linux.process_stats" target_buffer: 0 process_stats_config { scan_all_processes_on_start: true } } }
data_sources { config { name: "android.surfaceflinger.frametimeline" } }
data_sources { config { name: "linux.ftrace" ftrace_config {
  ftrace_events: "sched/sched_switch"
  ftrace_events: "sched/sched_wakeup"
  ftrace_events: "sched/sched_waking"
  ftrace_events: "sched/sched_blocked_reason"
  ftrace_events: "power/cpu_frequency"
  ftrace_events: "power/suspend_resume"
  atrace_categories: "am" atrace_categories: "wm" atrace_categories: "gfx" atrace_categories: "view"
  atrace_categories: "dalvik" atrace_categories: "binder_driver" atrace_categories: "input"
  atrace_categories: "res" atrace_categories: "ss" atrace_categories: "aidl" atrace_categories: "database"
  atrace_apps: "$PKG"
} } }
duration_ms: $((SECS * 1000))
EOF
)
s "am force-stop $PKG"
sleep 2
echo "$CFG" | "$ADB" -s "$SERIAL" shell "perfetto --txt -c - -o /data/misc/perfetto-traces/$LABEL.pftrace --background" | tr -d '\r'
sleep 2
s "am start -W -n $ACT" | grep -E "TotalTime"
sleep "$SECS"
a pull "/data/misc/perfetto-traces/$LABEL.pftrace" "$(cygpath -m "$OUT")/$LABEL.pftrace" | tail -1
s "rm -f /data/misc/perfetto-traces/$LABEL.pftrace"
