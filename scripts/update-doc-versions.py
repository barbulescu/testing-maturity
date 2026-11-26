#!/usr/bin/env python3
"""Sets the probe version the docs show to the given release: update-doc-versions.py 0.2.0

The release workflow runs it after publishing, so the docs always show the latest release.
Only version numbers next to the probe's coordinates change. Examples of a future release,
such as `git tag v0.2.0` in "Releasing", are left alone.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = ["README.md", "gitlab-component/README.md"]
VERSION = r"\d+\.\d+\.\d+"
PATTERNS = [
    # <artifactId>testability-probe</artifactId> followed by <version>
    rf"(<artifactId>testability-probe(?:-maven-extension)?</artifactId>\s*<version>){VERSION}(</version>)",
    # ch.barbulescu.testability:testability-probe:0.1.0
    rf"(ch\.barbulescu\.testability:testability-probe(?:-maven-extension)?:){VERSION}()",
    # testability-probe-maven-extension-0.1.0.jar
    rf"(testability-probe(?:-maven-extension)?-){VERSION}(\.jar)",
    # "probeVersion": "0.1.0" in the report example
    rf'("probeVersion": "){VERSION}(")',
    # version: 0.1.0 as a component input
    rf"(^\s+version: ){VERSION}()$",
]

if len(sys.argv) != 2 or not re.fullmatch(VERSION, sys.argv[1]):
    sys.exit("usage: update-doc-versions.py X.Y.Z")
version = sys.argv[1]

for doc in DOCS:
    path = ROOT / doc
    text = path.read_text(encoding="utf-8")
    updated = text
    for pattern in PATTERNS:
        updated = re.sub(pattern, lambda m: m.group(1) + version + m.group(2), updated, flags=re.M)
    if updated != text:
        path.write_text(updated, encoding="utf-8")
        print(f"{doc}: now shows {version}")
    else:
        print(f"{doc}: already shows {version}")
