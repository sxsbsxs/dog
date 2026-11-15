#!/usr/bin/env python3
"""Verify Android APK native ABI payloads for yt-dlp, FFmpeg, aria2c and VideoFetch."""

from __future__ import annotations

import argparse
import sys
import zipfile
from collections import defaultdict
from pathlib import Path

# Names mirror the Android runtime's JNI libraries and its packaged tool archives.
REQUIRED_PER_ABI = {
    "libpython.so",
    "libpython.zip.so",
    "libqjs.so",
    "libffmpeg.so",
    "libffmpeg.zip.so",
    "libaria2c.zip.so",
    "libvideofetchmedia.so",
}


def inspect_apk(apk: Path) -> int:
    if not apk.is_file():
        print(f"APK not found: {apk}", file=sys.stderr)
        return 2

    found: dict[str, set[str]] = defaultdict(set)
    with zipfile.ZipFile(apk) as archive:
        for entry in archive.namelist():
            parts = entry.split("/")
            if len(parts) == 3 and parts[0] == "lib":
                found[parts[1]].add(parts[2])

    if not found:
        print("APK contains no native Android ABI libraries.", file=sys.stderr)
        return 1

    failures: list[str] = []
    for abi in sorted(found):
        missing = sorted(REQUIRED_PER_ABI - found[abi])
        if missing:
            failures.append(f"{abi}: missing {', '.join(missing)}")
        else:
            print(f"{abi}: CPython + QuickJS + FFmpeg + aria2c + videofetchmedia OK")

    if failures:
        print("\n".join(failures), file=sys.stderr)
        return 1

    print(f"Validated {len(found)} ABI directories in {apk.name}.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    args = parser.parse_args()
    return inspect_apk(args.apk)


if __name__ == "__main__":
    raise SystemExit(main())
