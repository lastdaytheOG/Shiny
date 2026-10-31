"""Usage: trace_report.py <trace.pftrace> <package>. Summarises one cold start from a Perfetto trace."""
import sys
from perfetto.trace_processor import TraceProcessor

trace, pkg = sys.argv[1], sys.argv[2]
tp = TraceProcessor(trace=trace)


def rows(sql):
    return list(tp.query(sql))


start = rows(f"""
    SELECT ts, dur FROM slice WHERE name LIKE 'launching: {pkg}%' ORDER BY ts LIMIT 1
""")
t0 = start[0].ts if start else rows("SELECT MIN(ts) AS ts FROM slice")[0].ts
print("launch slice ms", round(start[0].dur / 1e6, 1) if start else "not found")

print("\n-- Shiny sections (start after launch, ms)")
for r in rows("""
    SELECT s.name AS name, s.ts AS ts, s.dur AS dur FROM slice s
    WHERE s.name LIKE 'Shiny%' ORDER BY s.ts
"""):
    print(f"{r.name:24s} at {(r.ts - t0) / 1e6:8.1f}  dur {r.dur / 1e6:8.1f}")

print("\n-- App threads, on-CPU ms in the first 8 s")
for r in rows(f"""
    SELECT t.name AS name, SUM(ss.dur) / 1e6 AS ms FROM sched_slice ss
    JOIN thread t USING (utid) JOIN process p USING (upid)
    WHERE p.name = '{pkg}' AND ss.ts < {t0} + 8000000000
    GROUP BY t.name ORDER BY ms DESC LIMIT 16
"""):
    print(f"{r.name or '?':28s} {r.ms:8.1f}")

print("\n-- Longest main thread slices in the first 8 s (ms)")
for r in rows(f"""
    SELECT s.name AS name, s.ts AS ts, s.dur AS dur FROM slice s
    JOIN thread_track tt ON s.track_id = tt.id
    JOIN thread t USING (utid) JOIN process p USING (upid)
    WHERE p.name = '{pkg}' AND t.tid = p.pid AND s.depth <= 2 AND s.ts < {t0} + 8000000000
    ORDER BY s.dur DESC LIMIT 25
"""):
    print(f"{r.dur / 1e6:8.1f} at {(r.ts - t0) / 1e6:8.1f}  {r.name[:90]}")

print("\n-- Database queries on the main thread")
for r in rows(f"""
    SELECT s.name AS name, COUNT(*) AS n, SUM(s.dur) / 1e6 AS ms FROM slice s
    JOIN thread_track tt ON s.track_id = tt.id
    JOIN thread t USING (utid) JOIN process p USING (upid)
    WHERE p.name = '{pkg}' AND t.tid = p.pid AND (s.name LIKE 'sqlite%' OR s.name LIKE 'db.%' OR s.name LIKE 'SELECT%')
    GROUP BY s.name ORDER BY ms DESC LIMIT 10
"""):
    print(f"{r.ms:8.1f} x{r.n:<4d} {r.name[:80]}")

print("\n-- Frames")
for r in rows(f"""
    SELECT COUNT(*) AS n, SUM(jank_type != 'None') AS janky,
           AVG(dur) / 1e6 AS avg_ms, MAX(dur) / 1e6 AS max_ms
    FROM actual_frame_timeline_slice f JOIN process p USING (upid) WHERE p.name = '{pkg}'
"""):
    print(f"frames={r.n} janky={r.janky} avg={r.avg_ms} max={r.max_ms}")
tp.close()
