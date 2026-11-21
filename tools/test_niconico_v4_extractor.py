#!/usr/bin/env python3
"""Offline contract tests for VideoFetch's Niconico v3 / v3_guest / V4 DMS extractor."""

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
    def __init_subclass__(cls, plugin_name=None, **kwargs):
        super().__init_subclass__(**kwargs)
        cls.plugin_name = plugin_name

    def __init__(self, page="", api_responses=None):
        self.page = page
        self.api_responses = list(api_responses or [])
        self.api_requests = []
        self.options_request = None
        self.access_request = None
        self.manifest_url = None
        self.debug_messages = []

    @property
    def is_logged_in(self):
        return False

    def _match_id(self, url):
        return url.rstrip("/").split("/")[-1]

    def write_debug(self, message):
        self.debug_messages.append(message)

    def _download_webpage(self, url, video_id, **kwargs):
        return self.page

    def _request_webpage(self, request, video_id, **kwargs):
        self.options_request = request
        return object()

    def _download_json(self, url, video_id, **kwargs):
        if "/api/watch/" in url:
            self.api_requests.append((url, kwargs))
            if not self.api_responses:
                raise ExtractorError("No fake Niconico API response configured")
            result = self.api_responses.pop(0)
            if isinstance(result, Exception):
                raise result
            return result

        if "/access-rights/hls" in url:
            self.access_request = (url, kwargs)
            return {"data": {"contentUrl": "https://cdn.example.test/master.m3u8"}}

        raise AssertionError("Unexpected JSON request: " + url)

    def _extract_formats(self, api_data, video_id):
        return None

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
    / "app/src/main/assets/yt-dlp-plugins/videofetch/yt_dlp_plugins/extractor/niconico.py"
)
spec = importlib.util.spec_from_file_location("videofetch_niconico_plugin_test", plugin_path)
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
    "client": {"watchTrackId": "track-from-watch-api"},
    "media": {
        "accessRightKey": "access-key-from-watch-api",
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
api_response = {"meta": {"status": 200}, "data": watch_data}


def assert_dms_request(extractor, expected_track_id, expected_access_key="access-key-from-watch-api"):
    url, request = extractor.access_request
    assert url.startswith("https://nvapi.nicovideo.jp/v1/watch/sm37593092/access-rights/hls?")
    assert expected_track_id in url
    assert request["headers"]["X-Access-Right-Key"] == expected_access_key
    assert request["headers"]["X-Request-With"] == "https://www.nicovideo.jp"
    payload = json.loads(request["data"].decode())
    assert payload["outputs"] == [
        ["video-1080", "audio-high"],
        ["video-720", "audio-high"],
    ], payload["outputs"]
    assert "video-360-unavailable" not in request["data"].decode()
    assert "audio-unavailable" not in request["data"].decode()
    # Direct yt-dlp HTTP calls must not perform a browser-only CORS preflight.
    assert extractor.options_request is None
    assert extractor.manifest_url == "https://cdn.example.test/master.m3u8"


# Standard v3 succeeds. The plugin must not unnecessarily use v3_guest.
standard = plugin_module.VideoFetchNiconicoIE(api_responses=[api_response])
standard_result = standard._real_extract(
    "https://www.nicovideo.jp/watch/sm37593092")
assert standard_result["videofetch_niconico_api_mode"] == "v3-dms"
assert [item["height"] for item in standard_result["formats"]] == [1080, 720]
assert len(standard.api_requests) == 1
standard_url, standard_request = standard.api_requests[0]
assert "/api/watch/v3/sm37593092" in standard_url
assert standard_request["headers"]["X-Frontend-Id"] == "6"
assert standard_request["headers"]["X-Frontend-Version"] == "0"
standard_track = standard_request["query"]["actionTrackId"]
assert standard_track.startswith("AAAAAAAAAA_")
assert standard_request["query"]["_frontendId"] == "6"
assert standard_request["query"]["_frontendVersion"] == "0"
assert standard_request["query"]["skips"] == "harmful"
assert standard_request["query"]["noSideEffect"] == "false"
assert standard_request["query"]["t"] == standard_track.split("_", 1)[1]
assert_dms_request(standard, "track-from-watch-api")

# An empty contents placeholder must not hide valid streams in media.domand.
domand_data = json.loads(json.dumps(watch_data))
domand_data["media"]["domand"] = {
    "accessRightKey": "domand-access-key",
    "videos": domand_data["media"]["contents"]["videos"],
    "audios": domand_data["media"]["contents"]["audios"],
}
domand_data["media"]["contents"] = {}
domand_data["media"].pop("accessRightKey", None)
domand_only = plugin_module.VideoFetchNiconicoIE(
    api_responses=[{"meta": {"status": 200}, "data": domand_data}])
domand_result = domand_only._real_extract(
    "https://www.nicovideo.jp/watch/sm37593092")
assert domand_result["videofetch_niconico_api_mode"] == "v3-dms"
assert [item["height"] for item in domand_result["formats"]] == [1080, 720]
assert_dms_request(domand_only, "track-from-watch-api", "domand-access-key")

# Standard v3 failure falls back to v3_guest, reusing the actionTrackId.
guest = plugin_module.VideoFetchNiconicoIE(
    api_responses=[ExtractorError("standard API denied"), api_response])
guest_result = guest._real_extract("https://www.nicovideo.jp/watch/sm37593092")
assert guest_result["videofetch_niconico_api_mode"] == "v3_guest-dms"
assert len(guest.api_requests) == 2
guest_standard_url, guest_standard_request = guest.api_requests[0]
guest_url, guest_request = guest.api_requests[1]
assert "/api/watch/v3/sm37593092" in guest_standard_url
assert "/api/watch/v3_guest/sm37593092" in guest_url
assert guest_standard_request["query"]["actionTrackId"] == guest_request["query"]["actionTrackId"]
assert_dms_request(guest, "track-from-watch-api")

# When both APIs fail, the V4 server-response route remains available.
wrapper = {"data": {"response": {"$watchV4": {"data": watch_data}}}}
encoded = html.escape(json.dumps(wrapper), quote=True)
page = f'<html><head><meta name="server-response" content="{encoded}"></head></html>'
v4 = plugin_module.VideoFetchNiconicoIE(
    page,
    api_responses=[
        ExtractorError("standard API denied"),
        ExtractorError("guest API denied"),
    ])
v4_result = v4._real_extract("https://www.nicovideo.jp/watch/sm37593092")
assert v4_result["videofetch_niconico_api_mode"] == "watchV4-dms"
assert [item["height"] for item in v4_result["formats"]] == [1080, 720]

print("Niconico v3-first / guest-fallback / V4-DMS contract tests passed")
