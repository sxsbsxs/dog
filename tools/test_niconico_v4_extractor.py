#!/usr/bin/env python3
"""Offline contract test for Dog's Niconico V4/DMS yt-dlp plugin."""

import html
import importlib.util
import json
import re
import sys
import types
from pathlib import Path


def extract_attributes(tag):
    return dict(re.findall(r"""([:\w-]+)\s*=\s*["'](.*?)["']""", tag))


class ExtractorError(Exception):
    pass


class FakeNiconicoIE:
    def __init__(self, page):
        self.page = page
        self.options_request = None
        self.access_request = None
        self.manifest_url = None

    @property
    def is_logged_in(self):
        return False

    def _match_id(self, url):
        return url.rstrip("/").split("/")[-1]

    def _download_webpage(self, url, video_id, **kwargs):
        return self.page

    def _request_webpage(self, request, video_id, **kwargs):
        self.options_request = request
        return object()

    def _download_json(self, url, video_id, **kwargs):
        self.access_request = (url, kwargs)
        return {"data": {"contentUrl": "https://cdn.example.test/master.m3u8"}}

    def _extract_m3u8_formats(self, url, video_id, ext, **kwargs):
        self.manifest_url = url
        return [
            {"format_id": "dms-1080", "height": 1080, "url": url + "?v=1080"},
            {"format_id": "dms-720", "height": 720, "url": url + "?v=720"},
        ]

    def _real_extract(self, url):
        return {"id": self._match_id(url), "formats": [], "upstream_fallback": True}


yt_dlp = types.ModuleType("yt_dlp")
yt_dlp.__path__ = []
extractor_package = types.ModuleType("yt_dlp.extractor")
extractor_package.__path__ = []
niconico_module = types.ModuleType("yt_dlp.extractor.niconico")
niconico_module.NiconicoIE = FakeNiconicoIE
utils_module = types.ModuleType("yt_dlp.utils")
utils_module.ExtractorError = ExtractorError
utils_module.extract_attributes = extract_attributes
utils_module.parse_iso8601 = lambda value: value
networking_package = types.ModuleType("yt_dlp.networking")
networking_package.__path__ = []
exceptions_module = types.ModuleType("yt_dlp.networking.exceptions")
exceptions_module.HTTPError = type("HTTPError", (Exception,), {})

sys.modules.update({
    "yt_dlp": yt_dlp,
    "yt_dlp.extractor": extractor_package,
    "yt_dlp.extractor.niconico": niconico_module,
    "yt_dlp.utils": utils_module,
    "yt_dlp.networking": networking_package,
    "yt_dlp.networking.exceptions": exceptions_module,
})

plugin_path = (
    Path(__file__).resolve().parents[1]
    / "app/src/main/assets/yt-dlp-plugins/dog/yt_dlp_plugins/extractor/niconico.py"
)
spec = importlib.util.spec_from_file_location("dog_niconico_plugin_test", plugin_path)
plugin_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(plugin_module)

watch_data = {
    "video": {
        "id": "sm37593092",
        "title": "久本桜",
        "duration": 42,
        "registeredAt": "2020-09-17T00:00:00+09:00",
        "thumbnail": {"ogp": "https://cdn.example.test/thumb.jpg"},
        "count": {"view": 1000, "like": 10, "comment": 20},
    },
    "owner": {"nickname": "test-uploader", "id": "123"},
    "client": {"watchTrackId": "track-from-watch-page"},
    "media": {
        "accessRightKey": "access-key-from-watch-page",
        "contents": {
            "videos": [
                {"id": "video-1080", "isAvailable": True},
                {"id": "video-720", "isAvailable": True},
                {"id": "video-360-unavailable", "isAvailable": False},
            ],
            "audios": [
                {"id": "audio-high", "isAvailable": True},
                {"id": "audio-unavailable", "isAvailable": False},
            ],
        },
    },
}
wrapper = {"data": {"response": {"$watchV4": {"data": watch_data}}}}
encoded = html.escape(json.dumps(wrapper), quote=True)
page = f'<html><head><meta name="server-response" content="{encoded}"></head></html>'

extractor = plugin_module.DogNiconicoIE(page)
result = extractor._extract_watch_page_dms(
    "https://www.nicovideo.jp/watch/sm37593092", "sm37593092")

assert result is not None, "watchV4/DMS payload was not extracted"
assert result["dog_niconico_api_mode"] == "watchV4-dms"
assert result["id"] == "sm37593092"
assert result["title"] == "久本桜"
assert result["uploader"] == "test-uploader"
assert [item["height"] for item in result["formats"]] == [1080, 720]

url, request = extractor.access_request
assert url.startswith("https://nvapi.nicovideo.jp/v1/watch/sm37593092/access-rights/hls?")
assert "track-from-watch-page" in url
assert request["headers"]["X-Access-Right-Key"] == "access-key-from-watch-page"
assert request["headers"]["X-Request-With"] == "nicovideo"
payload = json.loads(request["data"].decode())
assert payload["outputs"] == [
    ["video-1080", "audio-high"],
    ["video-720", "audio-high"],
], payload["outputs"]
assert "video-360-unavailable" not in request["data"].decode()
assert "audio-unavailable" not in request["data"].decode()
assert extractor.options_request.get_method() == "OPTIONS"
assert extractor.manifest_url == "https://cdn.example.test/master.m3u8"

# If the watch page has no DMS media, the plugin leaves the upstream fallback
# path available rather than inventing a stream.
fallback_extractor = plugin_module.DogNiconicoIE("<html><body>legacy page</body></html>")
assert fallback_extractor._extract_watch_page_dms(
    "https://www.nicovideo.jp/watch/sm9", "sm9") is None

print("Niconico V4/DMS contract test passed")
