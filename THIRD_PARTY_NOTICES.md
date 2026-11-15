# Third-party notices

## Seal Android UI

- Upstream: https://github.com/JunkFood02/Seal
- License: GNU General Public License v3.0 (GPL-3.0); see the repository's `LICENSE`.
- Adapted UI source: Compose download-queue and media-card presentation, selection-pill component, navigation drawer structure, settings-list component, media-history row presentation, bottom-sheet presentation, and the two empty-state vector illustrations.
- Modifications: adapted for the `video-dl` app name, existing UI state, screen navigation, Chinese labels, and existing video-dl actions. Download services, yt-dlp, FFmpeg, aria2c, extraction, cookie storage, and file handling remain video-dl implementations.
- Adaptation date: 2026-11-15.

video-dl is not an official Seal build and does not use Seal as its application name.

## Other third-party components

- **yt-dlp** — https://github.com/yt-dlp/yt-dlp — The Unlicense; text at `LICENSES/yt-dlp-UNLICENSE`.
- **youtubedl-android** — https://github.com/yausername/youtubedl-android — Android runtime wrapper; consult upstream license notices.
- **FFmpeg** — https://ffmpeg.org/ — multimedia processing runtime; check the packaged binary's build configuration and notices for the applicable license.
- **aria2c** — https://github.com/aria2/aria2 — download engine; consult upstream license notices.

Android binaries are downloaded from Maven Central at build time. Before distributing an APK, review current notices and license terms for all transitive native dependencies.
