#!/usr/bin/env python3
"""
Regenerates licenses/dependencies.json: the licence inventory of every library the Android app
ships, read from the libraries' own metadata rather than typed by hand.

For each module in the gms and foss release runtime classpaths it records:
  * the licence(s) declared in the module's Maven POM (following <parent> POMs when the module
    declares none), normalised to SPDX identifiers where the mapping is unambiguous;
  * every NOTICE / LICENSE / COPYING file packed inside the artifact itself (verbatim), because
    Apache-2.0 section 4(d) requires NOTICE contents to travel with the binary;
  * which flavours (gms, foss) ship it.

Corrections for modules whose POM is missing or silent live in licenses/overrides.json, each with
the evidence it rests on. Anything still unresolved is written with "spdx": ["UNKNOWN"] and listed at
the end of the run: it must be resolved by hand, never guessed.

It also fetches the standard text of every SPDX licence in use from the SPDX license-list-data
repository into licenses/texts/ (GPL-3.0 is not fetched: the app shows the repository's LICENSE).

Usage (from the repository root, with a JDK for Gradle on JAVA_HOME):
    python scripts/third_party_licenses.py
    python scripts/third_party_licenses.py --deps-gms gms.txt --deps-foss foss.txt   # reuse reports

Run it whenever a dependency changes; the unit test LicenseInventoryTest fails while
licenses/dependencies.json is older than gradle/libs.versions.toml's dependency set.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "licenses"
CACHE_DIR = ROOT / "build" / "third-party-licenses"
GRADLE_CACHE = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches" / "modules-2" / "files-2.1"
REPOSITORIES = [  # the order settings.gradle.kts resolves from
    "https://dl.google.com/dl/android/maven2/",
    "https://repo1.maven.org/maven2/",
    "https://jitpack.io/",
    "https://maven.aliyun.com/repository/public/",
]
CONFIGURATIONS = {
    "gms": "universalGmsReleaseRuntimeClasspath",
    "foss": "universalFossReleaseRuntimeClasspath",
}
SPDX_TEXT_URL = "https://raw.githubusercontent.com/spdx/license-list-data/main/text/{}.txt"

DEP_LINE = re.compile(
    r"[+\\]--- (?!project )([\w.\-]+):([\w.\-]+)(?::([\w.\-+]+))?(?: -> ([\w.\-+]+))?( \((?:c|\*|n)\))?"
)
NOTICE_NAME = re.compile(
    r"(?i)(^|/)(notice|license|licence|copying|contributors|third[-_]?party[^/]*)(\.[a-z]+)?$"
)

# POM licence names/URLs seen in this project's graph → SPDX. Order matters: first match wins.
SPDX_RULES: list[tuple[re.Pattern, str]] = [
    (re.compile(r"(?i)android software development kit|developer\.android\.com/studio/terms"), "LicenseRef-Android-SDK"),
    (re.compile(r"(?i)apache.*2|apache-2\.0|licenses/LICENSE-2\.0"), "Apache-2.0"),
    (re.compile(r"(?i)^mit\b|mit license|opensource\.org/licenses/mit"), "MIT"),
    (re.compile(r"(?i)bsd.?3|new bsd|revised bsd|bsd-3-clause|opensource\.org/licenses/bsd-3"), "BSD-3-Clause"),
    (re.compile(r"(?i)bsd.?2|simplified bsd|freebsd"), "BSD-2-Clause"),
    (re.compile(r"(?i)^bsd( license)?$"), "LicenseRef-BSD-unspecified"),
    (re.compile(r"(?i)eclipse public license.*1\.0|epl-1\.0|epl-v10"), "EPL-1.0"),
    (re.compile(r"(?i)eclipse public license.*2\.0|epl-2\.0"), "EPL-2.0"),
    (re.compile(r"(?i)mozilla public license.*2\.0|mpl-2\.0|mozilla\.org/MPL/2\.0"), "MPL-2.0"),
    (re.compile(r"(?i)lesser general public license.*3|lgpl.?3"), "LGPL-3.0"),
    (re.compile(r"(?i)lesser general public license.*2\.1|lgpl.?2\.1"), "LGPL-2.1"),
    (re.compile(r"(?i)general public license.*v?3|gpl.?3"), "GPL-3.0"),
    (re.compile(r"(?i)unlicense"), "Unlicense"),
    (re.compile(r"(?i)cc0|creative commons zero"), "CC0-1.0"),
    (re.compile(r"(?i)^isc\b|isc license"), "ISC"),
    (re.compile(r"(?i)bouncy castle"), "LicenseRef-Bouncy-Castle"),
]
# "GPL-3.0" / "LGPL-x" are used when a POM names the version without saying "only" or "or later";
# the licence text is the same either way. GPL-3.0 is shown from the repository's own LICENSE file.
TEXT_FILE_FOR = {"LGPL-3.0": "LGPL-3.0-only", "LGPL-2.1": "LGPL-2.1-only"}
NO_TEXT_FETCH = {"GPL-3.0", "UNKNOWN"}


def run_dependencies(config: str) -> str:
    gradlew = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    return subprocess.run(
        [str(gradlew), "-q", ":app:dependencies", "--configuration", config],
        cwd=ROOT, check=True, capture_output=True, text=True, encoding="utf-8",
    ).stdout


def parse_modules(report: str) -> dict[tuple[str, str], str]:
    mods: dict[tuple[str, str], str] = {}
    for line in report.splitlines():
        m = DEP_LINE.search(line)
        if not m:
            continue
        group, artifact, version, resolved, flag = m.groups()
        if flag and "(c)" in flag:  # a constraint, not a dependency
            continue
        if resolved or version:
            mods[(group, artifact)] = resolved or version
    return mods


def fetch(url: str) -> bytes | None:
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "shiny-license-inventory"}), timeout=30) as r:
            return r.read()
    except (urllib.error.URLError, TimeoutError):
        return None


def pom_bytes(group: str, artifact: str, version: str) -> bytes | None:
    local = list((GRADLE_CACHE / group / artifact / version).glob("*/*.pom"))
    if local:
        return local[0].read_bytes()
    cached = CACHE_DIR / "poms" / f"{group}__{artifact}__{version}.pom"
    if cached.exists():
        return cached.read_bytes() or None
    path = f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.pom"
    data = None
    for repo in REPOSITORIES:
        data = fetch(repo + path)
        if data and b"<project" in data:
            break
        data = None
    cached.parent.mkdir(parents=True, exist_ok=True)
    cached.write_bytes(data or b"")
    return data


def strip_ns(root: ET.Element) -> ET.Element:
    for el in root.iter():
        if isinstance(el.tag, str) and "}" in el.tag:
            el.tag = el.tag.split("}", 1)[1]
    return root


def parse_pom(data: bytes) -> ET.Element | None:
    try:
        return strip_ns(ET.fromstring(data))
    except ET.ParseError:
        return None


def text(el: ET.Element | None, path: str) -> str:
    found = el.find(path) if el is not None else None
    return (found.text or "").strip() if found is not None and found.text else ""


def pom_info(group: str, artifact: str, version: str) -> dict:
    """Name, URL, packaging and licences, walking up <parent> POMs for the licences."""
    info = {"name": "", "url": "", "packaging": "", "licenses": [], "licenseSource": "", "organization": "", "developers": []}
    g, a, v, depth = group, artifact, version, 0
    while depth < 6:
        data = pom_bytes(g, a, v)
        root = parse_pom(data) if data else None
        if root is None:
            break
        if depth == 0:
            info["name"] = text(root, "name")
            info["url"] = text(root, "url")
            info["packaging"] = text(root, "packaging")
            info["organization"] = text(root, "organization/name")
            info["developers"] = [d for d in (text(dev, "name") or text(dev, "id") for dev in root.findall("developers/developer")) if d]
        lic = [{"name": text(l, "name"), "url": text(l, "url")} for l in root.findall("licenses/license")]
        lic = [l for l in lic if l["name"] or l["url"]]
        if lic:
            info["licenses"] = lic
            info["licenseSource"] = "pom" if depth == 0 else f"parent pom {g}:{a}:{v}"
            break
        parent = root.find("parent")
        if parent is None:
            break
        g, a, v = text(parent, "groupId"), text(parent, "artifactId"), text(parent, "version")
        if not (g and a and v) or "${" in v:
            break
        depth += 1
    return info


def to_spdx(licenses: list[dict]) -> list[str]:
    out = []
    for lic in licenses:
        probe = f"{lic['name']} {lic['url']}".strip()
        for pattern, spdx in SPDX_RULES:
            if pattern.search(lic["name"]) or pattern.search(lic["url"]) or pattern.search(probe):
                out.append(spdx)
                break
        else:
            out.append("UNKNOWN")
    return sorted(set(out)) or ["UNKNOWN"]


def artifact_files(group: str, artifact: str, version: str) -> list[Path]:
    return sorted(
        p for p in (GRADLE_CACHE / group / artifact / version).glob("*/*")
        if p.suffix in (".aar", ".jar") and not p.name.endswith(("-sources.jar", "-javadoc.jar"))
    )


def embedded_notices(files: list[Path]) -> list[dict]:
    """Every NOTICE/LICENSE/COPYING file inside the artifact (and inside an AAR's classes.jar)."""
    found: dict[str, str] = {}

    def scan(zf: zipfile.ZipFile, prefix: str) -> None:
        for name in zf.namelist():
            if name.endswith("/"):
                continue
            if name.endswith(".jar") and prefix == "":
                try:
                    scan(zipfile.ZipFile(io.BytesIO(zf.read(name))), name + "!/")
                except zipfile.BadZipFile:
                    pass
                continue
            if NOTICE_NAME.search(name) and not name.endswith((".class", ".kotlin_module")):
                raw = zf.read(name)
                if len(raw) > 200_000:
                    continue
                found[prefix + name] = raw.decode("utf-8", "replace").replace("\r\n", "\n").strip()

    for f in files:
        try:
            scan(zipfile.ZipFile(f), "")
        except zipfile.BadZipFile:
            continue
    return [{"file": k, "text": v} for k, v in sorted(found.items())]


def normalise(t: str) -> str:
    return re.sub(r"\s+", " ", t).strip().lower()


def dependency_fingerprint() -> str:
    """Hash of every dependency declaration: the version catalog plus dependency lines of build files."""
    h = hashlib.sha256()
    h.update((ROOT / "gradle" / "libs.versions.toml").read_bytes().replace(b"\r\n", b"\n"))
    for build in sorted(ROOT.glob("*/build.gradle.kts")) + [ROOT / "build.gradle.kts"]:
        for line in build.read_text(encoding="utf-8").splitlines():
            s = line.strip()
            if re.match(r'^"?(\w+)?(implementation|api|runtimeOnly|coreLibraryDesugaring)"?\(', s, re.I):
                h.update(f"{build.relative_to(ROOT).as_posix()}:{s}\n".encode())
    return h.hexdigest()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--deps-gms")
    ap.add_argument("--deps-foss")
    args = ap.parse_args()

    reports = {
        "gms": Path(args.deps_gms).read_text(encoding="utf-8") if args.deps_gms else run_dependencies(CONFIGURATIONS["gms"]),
        "foss": Path(args.deps_foss).read_text(encoding="utf-8") if args.deps_foss else run_dependencies(CONFIGURATIONS["foss"]),
    }
    per_flavor = {flavor: parse_modules(rep) for flavor, rep in reports.items()}
    overrides = json.loads((OUT_DIR / "overrides.json").read_text(encoding="utf-8"))["modules"]

    modules: dict[tuple[str, str], str] = {}
    for mods in per_flavor.values():
        for key, version in mods.items():
            modules[key] = max(modules.get(key, version), version)

    apache_text = None
    apache_file = OUT_DIR / "texts" / "Apache-2.0.txt"
    if apache_file.exists():
        apache_text = normalise(apache_file.read_text(encoding="utf-8"))

    entries, unresolved, skipped = [], [], []
    notice_texts: dict[str, str] = {}
    for (group, artifact), version in sorted(modules.items()):
        files = artifact_files(group, artifact, version)
        info = pom_info(group, artifact, version)
        if not files:
            # BOMs and Kotlin-multiplatform umbrella modules carry metadata only; the platform
            # variant (…-android / …-jvm) that actually ships is its own entry.
            skipped.append(f"{group}:{artifact}:{version}")
            continue
        coordinate = f"{group}:{artifact}"
        override = overrides.get(coordinate)
        spdx = to_spdx(info["licenses"]) if info["licenses"] else ["UNKNOWN"]
        declared = [{"name": l["name"] or l["url"], "url": l["url"]} for l in info["licenses"]]
        source = info["licenseSource"] or "none"
        if override:
            if "spdx" in override:
                spdx = override["spdx"]
                declared = [{"name": d, "url": ""} for d in override["declared"]] if "declared" in override else declared
                source = "override: " + override["evidence"]
            info["url"] = override.get("url", info["url"])
        notices = embedded_notices(files)
        for n in notices:
            body = n.pop("text")
            if apache_text and normalise(body) == apache_text:
                # The full Apache text is shown from texts/; keep the file name as evidence.
                n["standardText"] = "Apache-2.0"
                continue
            # Many artifacts carry the same file; each distinct text is stored once.
            text_id = hashlib.sha1(body.encode("utf-8")).hexdigest()[:12]
            notice_texts[text_id] = body
            n["textId"] = text_id
        entry = {
            "id": f"{coordinate}:{version}",
            "name": (override or {}).get("name") or info["name"] or artifact,
            "group": group,
            "artifact": artifact,
            "version": version,
            "flavors": sorted(f for f, mods in per_flavor.items() if (group, artifact) in mods),
            "spdx": spdx,
            "declaredLicenses": declared,
            "licenseSource": source,
            "url": info["url"],
            "organization": info["organization"],
            "developers": info["developers"][:6],
            "notices": notices,
            "noticeFiles": (override or {}).get("noticeFiles", []),
        }
        if "UNKNOWN" in spdx:
            unresolved.append(entry["id"])
        entries.append(entry)

    used = sorted({s for e in entries for s in e["spdx"]})
    texts = OUT_DIR / "texts"
    texts.mkdir(parents=True, exist_ok=True)
    for spdx in used:
        target = texts / f"{spdx}.txt"
        if spdx in NO_TEXT_FETCH or spdx.startswith("LicenseRef-") or target.exists():
            continue
        data = fetch(SPDX_TEXT_URL.format(TEXT_FILE_FOR.get(spdx, spdx)))
        if data:
            target.write_bytes(data.replace(b"\r\n", b"\n"))
        else:
            print(f"warning: could not fetch the SPDX text for {spdx}", file=sys.stderr)

    out = {
        "_comment": "Generated by scripts/third_party_licenses.py from Maven POMs and the artifacts themselves. Do not edit; fix licenses/overrides.json and regenerate.",
        "dependencyFingerprint": dependency_fingerprint(),
        "configurations": CONFIGURATIONS,
        "licensesUsed": used,
        "libraries": entries,
        "noticeTexts": dict(sorted(notice_texts.items())),
    }
    (OUT_DIR / "dependencies.json").write_text(json.dumps(out, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")

    print(f"{len(entries)} shipped modules ({len(skipped)} metadata-only modules skipped)")
    for spdx in used:
        print(f"  {spdx}: {sum(1 for e in entries if spdx in e['spdx'])}")
    if unresolved:
        print("UNRESOLVED licences (add evidence to licenses/overrides.json):", *unresolved, sep="\n  ")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
