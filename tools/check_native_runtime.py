#!/usr/bin/env python3
"""Check that the Android APK contains the native libraries Dog needs."""

from __future__ import annotations

import sys
import zipfile
from pathlib import Path

REQUIRED_LIBRARIES = {
    "libpython.so",   # CPython runtime used by yt-dlp
    "libqjs.so",      # JavaScript runtime used by yt-dlp extractors
    "libffmpeg.so",   # FFmpeg native media processing runtime
    "libaria2c.so",   # aria2 external downloader
    "libdogmedia.so", # Dog's own C++ JNI helper
}


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: check_native_runtime.py path/to/app-debug.apk", file=sys.stderr)
        return 2

    apk = Path(sys.argv[1])
    if not apk.is_file():
        print(f"APK not found: {apk}", file=sys.stderr)
        return 2

    with zipfile.ZipFile(apk) as archive:
        names = set(archive.namelist())

    abi_dirs = sorted({
        name.split("/")[1]
        for name in names
        if name.startswith("lib/") and name.count("/") >= 2
    })
    if not abi_dirs:
        print("No native ABI directories found in APK", file=sys.stderr)
        return 1

    failures: list[str] = []
    for abi in abi_dirs:
        present = {
            Path(name).name
            for name in names
            if name.startswith(f"lib/{abi}/")
        }
        missing = sorted(REQUIRED_LIBRARIES - present)
        if missing:
            failures.append(f"{abi}: missing {', '.join(missing)}")
        else:
            print(f"{abi}: Python, QuickJS, FFmpeg, aria2c and dogmedia OK")

    if failures:
        print("\n".join(failures), file=sys.stderr)
        return 1

    print(f"Verified {len(abi_dirs)} Android ABI(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
