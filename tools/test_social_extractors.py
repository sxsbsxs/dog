#!/usr/bin/env python3
"""Offline contract tests for VideoFetch's Douyin share/SSR extractor."""

import importlib.util
import json
import re
import sys
import types
from pathlib import Path
from urllib.parse import urlparse


class ExtractorError(Exception):
    pass


class FakeResponseHandle:
    def __init__(self, url):
        self.url = url

    def geturl(self):
        return self.url


class FakeDouyinIE:
    def __init_subclass__(cls, plugin_name=None, **kwargs):
        super().__init_subclass__(**kwargs)
        cls.plugin_name = plugin_name

    def __init__(self, page="", redirected_url="", fail_upstream=False):
        self.page = page
        self.redirected_url = redirected_url
        self.fail_upstream = fail_upstream
        self.debug_messages = []
        self.requested_pages = []

    @classmethod
    def _match_valid_url(cls, url):
        return re.match(cls._VALID_URL, url)

    def _download_webpage_handle(self, url, video_id, **kwargs):
        return "", FakeResponseHandle(self.redirected_url or url)

    def _download_webpage(self, url, video_id, **kwargs):
        self.requested_pages.append((url, kwargs))
        return self.page

    def _parse_aweme_video_app(self, detail):
        addr = detail.get("video", {}).get("play_addr", {})
        formats = [
            {"url": item, "format_id": "play_addr"}
            for item in addr.get("url_list", [])
        ]
        return {
            "id": str(detail.get("aweme_id") or detail.get("id")),
            "title": detail.get("desc") or "Douyin test video",
            "formats": formats,
        }

    def _real_extract(self, url):
        if self.fail_upstream:
            raise ExtractorError("simulated upstream detail API failure")
        return {
            "id": self._match_valid_url(url).group("id"),
            "title": "upstream fallback",
            "formats": [{"url": "https://cdn.example.test/upstream.mp4"}],
        }

    def write_debug(self, message):
        self.debug_messages.append(message)


yt_dlp = types.ModuleType("yt_dlp")
yt_dlp.__path__ = []
extractor_package = types.ModuleType("yt_dlp.extractor")
extractor_package.__path__ = []
tiktok_module = types.ModuleType("yt_dlp.extractor.tiktok")
tiktok_module.DouyinIE = FakeDouyinIE
utils_module = types.ModuleType("yt_dlp.utils")
utils_module.ExtractorError = ExtractorError

sys.modules.update({
    "yt_dlp": yt_dlp,
    "yt_dlp.extractor": extractor_package,
    "yt_dlp.extractor.tiktok": tiktok_module,
    "yt_dlp.utils": utils_module,
})

plugin_path = (
    Path(__file__).resolve().parents[1]
    / "app/src/main/assets/yt-dlp-plugins/videofetch/yt_dlp_plugins/extractor/douyin.py"
)
spec = importlib.util.spec_from_file_location("videofetch_douyin_plugin_test", plugin_path)
plugin_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(plugin_module)
Plugin = plugin_module.VideoFetchDouyinIE


# Full video URLs, the mobile share-page URL and public short links are all routed here.
assert re.match(Plugin._VALID_URL, "https://www.douyin.com/video/123456789")
assert re.match(Plugin._VALID_URL, "https://v.douyin.com/AbCd123/")
assert re.match(Plugin._VALID_URL, "https://www.iesdouyin.com/share/video/123456789/")

# SSR payload shape used by the mobile share page:
# loaderData["video_(id)/page"].videoInfoRes.item_list[0].
video_id = "123456789"
detail = {
    "aweme_id": video_id,
    "desc": "SSR test",
    "author": {"nickname": "creator"},
    "video": {
        "play_addr": {
            "url_list": [
                "https://cdn.example.test/video/playwm/test.mp4",
            ],
        },
    },
}
payload = {
    "loaderData": {
        f"video_({video_id})/page": {
            "videoInfoRes": {"item_list": [detail]},
        },
    },
}
page = "window._ROUTER_DATA = " + json.dumps(payload) + ";"

# Direct full URL uses SSR without calling the upstream API.
direct = Plugin(page=page)
result = direct._real_extract(f"https://www.douyin.com/video/{video_id}")
assert result["id"] == video_id
assert result["title"] == "SSR test"
assert result["formats"][0]["url"].endswith("test.mp4")
assert result["formats"][0]["http_headers"]["Referer"] == "https://www.douyin.com/"
assert direct.requested_pages[0][0] == f"https://www.iesdouyin.com/share/video/{video_id}/"

# Share short links are resolved before SSR extraction.
short = Plugin(
    page=page,
    redirected_url=f"https://www.iesdouyin.com/share/video/{video_id}/",
)
short_result = short._real_extract("https://v.douyin.com/AbCd123/")
assert short_result["id"] == video_id

# If SSR has no item data, the upstream Douyin extractor remains the fallback.
fallback = Plugin(page="<html>empty CSR shell</html>")
fallback_result = fallback._real_extract(f"https://www.douyin.com/video/{video_id}")
assert fallback_result["title"] == "upstream fallback"

# If both data paths fail, return an actionable expected extractor error.
failed = Plugin(page="<html>empty CSR shell</html>", fail_upstream=True)
try:
    failed._real_extract(f"https://www.douyin.com/video/{video_id}")
except ExtractorError as error:
    assert "mobile share page or yt-dlp API" in str(error)
else:
    raise AssertionError("Expected a clear error when both Douyin paths fail")

# Balanced JSON extraction must tolerate nested objects and braces inside strings.
nested = {"value": {"text": "brace } and quote \\""}, "list": [1, 2, 3]}
wrapped = "window._ROUTER_DATA = " + json.dumps(nested) + ";"
assert Plugin._json_object_after(wrapped, "window._ROUTER_DATA") == nested

print("Douyin share URL / SSR parsing / upstream fallback contract tests passed")
