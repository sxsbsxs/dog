#!/usr/bin/env python3
"""Compile the C CRC32 utility and compare it with Python's zlib implementation."""

from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NATIVE_DIR = ROOT / "app" / "src" / "main" / "cpp"
C_SOURCE = NATIVE_DIR / "media_crc32.c"
HEADER = NATIVE_DIR / "media_crc32.h"

HARNESS = r"""
#include "media_crc32.h"
#include <inttypes.h>
#include <stdio.h>

int main(int argc, char** argv) {
    uint64_t byte_count = 0;
    uint32_t checksum = 0;
    if (argc != 2) return 2;

    if (dog_crc32_file(argv[1], &byte_count, &checksum) != 0) {
        return 3;
    }

    printf("%" PRIu64 ":%08" PRIx32 "\n", byte_count, checksum);
    return 0;
}
"""


def main() -> int:
    compiler = os.environ.get("CC", "cc")
    if shutil.which(compiler) is None:
        raise SystemExit(f"C compiler not found: {compiler}")
    if not C_SOURCE.is_file() or not HEADER.is_file():
        raise SystemExit("Native C checksum source files are missing")

    payload = bytes(range(256)) * 4096 + b"Dog downloader native integrity test\x00\xff"
    expected = f"{len(payload)}:{zlib.crc32(payload) & 0xFFFFFFFF:08x}"

    with tempfile.TemporaryDirectory(prefix="dog-crc32-") as temp_dir:
        temp = Path(temp_dir)
        input_file = temp / "sample-media.bin"
        harness_file = temp / "crc32_harness.c"
        executable = temp / "crc32_harness"

        input_file.write_bytes(payload)
        harness_file.write_text(HARNESS, encoding="utf-8")

        subprocess.run(
            [
                compiler,
                "-std=c11",
                "-Wall",
                "-Wextra",
                "-Werror",
                "-I",
                str(NATIVE_DIR),
                str(C_SOURCE),
                str(harness_file),
                "-o",
                str(executable),
            ],
            check=True,
        )

        result = subprocess.run(
            [str(executable), str(input_file)],
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()

    if result != expected:
        raise SystemExit(
            f"C CRC32 mismatch: expected {expected}, received {result or '<empty>'}"
        )

    print(f"C CRC32 matches Python zlib: {result}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
