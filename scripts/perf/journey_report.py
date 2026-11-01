"""Usage: journey_report.py <journeys dir>... Prints frame and section medians from each run's JSON."""
import glob, json, os, sys

for d in sys.argv[1:]:
    for f in sorted(glob.glob(d + '/*.json')):
        for b in json.load(open(f))['benchmarks']:
            out = [f"{os.path.basename(d):14s} {b['name']:16s}"]
            for k, v in b.get('sampledMetrics', {}).items():
                out.append(f"{k} p50 {v['P50']:.1f} p90 {v['P90']:.1f} p99 {v['P99']:.1f} n={len(v['runs'][0]) if v.get('runs') else '?'}")
            for k, v in b.get('metrics', {}).items():
                out.append(f"{k} med {v['median']:.1f}")
            print(' | '.join(out))
