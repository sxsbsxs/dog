#!/usr/bin/env python3
"""Compile and exercise the pure-Java URL normalizer without Android dependencies."""

import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/sxsbsxs/videofetch/VideoUrlNormalizer.java"

HARNESS = r"""
import com.sxsbsxs.videofetch.VideoUrlNormalizer;

public final class UrlNormalizerContract {
    private static void expect(String input, String expected) {
        String actual = VideoUrlNormalizer.normalize(input);
        if (!expected.equals(actual)) {
            throw new AssertionError(input + " -> " + actual + " (expected " + expected + ")");
        }
    }

    public static void main(String[] args) {
        expect("https://nicovideodl.jp/watch/sm37593092",
                "https://www.nicovideo.jp/watch/sm37593092");
        expect("https://www.nicovideodl.jp/watch/sm37593092?ref=share#top",
                "https://www.nicovideo.jp/watch/sm37593092");
        expect("http://nicovideodl.jp/watch/so12345678/",
                "https://www.nicovideo.jp/watch/so12345678");
        expect("https://nicovideodl.jp/watch/not-a-video",
                "https://nicovideodl.jp/watch/not-a-video");
        expect("https://example.com/watch/sm37593092",
                "https://example.com/watch/sm37593092");
        expect("https://youtu.be/HJQh7iEnepY?si=abc",
                "https://youtu.be/HJQh7iEnepY?si=abc");
        if (VideoUrlNormalizer.normalize("not a URL") != null) {
            throw new AssertionError("invalid input must be rejected");
        }
        System.out.println("URL normalizer contract tests passed");
    }
}
"""

with tempfile.TemporaryDirectory() as directory:
    temp = Path(directory)
    harness = temp / "UrlNormalizerContract.java"
    harness.write_text(HARNESS, encoding="utf-8")
    subprocess.run(
        ["javac", "-d", str(temp), str(SOURCE), str(harness)],
        check=True,
    )
    subprocess.run(
        ["java", "-cp", str(temp), "UrlNormalizerContract"],
        check=True,
    )
