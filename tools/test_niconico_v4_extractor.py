#!/usr/bin/env python3
"""Offline contract tests for Niconico NVAPI v4, DMS and legacy fallbacks."""

import html
import importlib.util
import json
import re
import sys
import types
from pathlib import Path


class ExtractorError(Exception):
    pass


class FakeCookie:
    def __init__(self, value):
        self.value = value


class FakeNiconicoIE:
    def __init_subclass__(cls, plugin_name=None, **kwargs):
        super().__init_subclass__(**kwargs)
        cls.plugin_name = plugin_name

    def __init__(self, page="", v4_responses=None, api_responses=None, logged_in=False):
        self.page = page
        self.v4_responses = list(v4_responses or [])
        self.api_responses = list(api_responses or [])
        self.logged_in = logged_in
        self.v4_requests = []
        self.api_requests = []
        self.access_request = None
        self.m3u8_requests = []
        self.cookies = {}
        self.debug_messages = []

    @property
    def is_logged_in(self):
        return self.logged_in

    def _get_cookies(self, url):
        return self.cookies

    def _match_id(self, url):
        return url.rstrip("/").split("/")[-1]

    def write_debug(self, message):
        self.debug_messages.append(message)

    def _download_webpage(self, url, video_id, **kwargs):
        return self.page

    def _download_json(self, url, video_id, **kwargs):
        if "/v4/watch/" in url:
            self.v4_requests.append((url, kwargs))
            if not self.v4_responses:
                raise ExtractorError("No fake v4 response configured")
            result = self.v4_responses.pop(0)
            if isinstance(result, Exception):
                raise result
            if result.pop("_set_domand_bid", False):
                self.cookies["domand_bid"] = FakeCookie("fresh-domand-bid")
            return result

        if "/api/watch/" in url:
            self.api_requests.append((url, kwargs))
            if not self.api_responses:
                raise ExtractorError("No fake legacy API response configured")
            result = self.api_responses.pop(0)
            if isinstance(result, Exception):
                raise result
            return result

        if "/access-rights/hls" in url:
            self.access_request = (url, kwargs)
            self.cookies["domand_bid"] = FakeCookie("rights-domand-bid")
            return {
                "meta": {"status": 201},
                "data": {"contentUrl": "https://cdn.example.test/master.m3u8"},
            }

        raise AssertionError("Unexpected JSON request: " + url)

    def _extract_formats(self, api_data, video_id):
        return None

    def _extract_m3u8_formats(self, url, video_id, ext, **kwargs):
        self.m3u8_requests.append((url, kwargs))
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
utils_module.extract_attributes = lambda tag: dict(
    re.findall(r"""([:\w-]+)\s*=\s*["'](.*?)["']""", tag))
utils_module.parse_iso8601 = lambda value: value
sys.modules.update({
    "yt_dlp": yt_dlp,
    "yt_dlp.extractor": extractor_package,
    "yt_dlp.extractor.niconico": niconico_module,
    "yt_dlp.utils": utils_module,
})

plugin_path = (
    Path(__file__).resolve().parents[1]
    / "app/src/main/assets/yt-dlp-plugins/videofetch/yt_dlp_plugins/extractor/niconico.py"
)
spec = importlib.util.spec_from_file_location("videofetch_niconico_plugin_test", plugin_path)
plugin_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(plugin_module)


def make_v4_data(preissued=True, guest=True):
    media = {
        "contents": {
            "videos": [
                {"id": "video-h264-1080p", "isAvailable": True, "qualityLevel": 4,
                 "label": "1080p", "width": 1920, "height": 1080, "bitRate": 4000000},
                {"id": "video-h264-720p", "isAvailable": True, "qualityLevel": 3,
                 "label": "720p", "width": 1280, "height": 720, "bitRate": 2000000},
                {"id": "video-h264-360p", "isAvailable": False, "qualityLevel": 1,
                 "label": "360p", "width": 640, "height": 360, "bitRate": 400000},
            ],
            "audios": [
                {"id": "audio-aac-192kbps", "isAvailable": True, "qualityLevel": 1,
                 "bitRate": 192000},
                {"id": "audio-aac-64kbps", "isAvailable": False, "qualityLevel": 0,
                 "bitRate": 64000},
            ],
        },
        "accessRightKey": "fake-v4-access-key",
        "hls": {
            "url": "https://delivery.domand.nicovideo.jp/preissued/master.m3u8?sig=redacted",
            "outputs": [
                {"assetUnitNames": ["video-h264-1080p", "audio-aac-192kbps"]},
                {"assetUnitNames": ["video-h264-720p", "audio-aac-192kbps"]},
            ],
            "expiredAt": "2999-01-01T00:00:00+00:00",
        } if preissued else None,
    }
    return {
        "meta": {"status": 200},
        "data": {
            "client": {
                "watchId": "sm37593092",
                "watchTrackId": "WatchTrack1_1700000000000",
            },
            "video": {
                "id": "sm37593092",
                "title": "Niconico v4 fixture",
                "duration": 42,
                "registeredAt": "2020-09-17T00:00:00+09:00",
                "thumbnail": {"normal": "https://cdn.example.test/thumb.jpg"},
                "count": {"view": 1000, "like": 10, "comment": 20},
            },
            "metadata": {"jsonLd": {"owner": {"id": "123", "name": "test-uploader"}}},
            "viewer": None if guest else {"id": 456, "isPremium": True},
            "media": media,
            "okReason": "PURELY",
        },
        "_set_domand_bid": True,
    }


# Current v4 guest path: no user_session is sent; pre-issued HLS uses domand_bid.
v4_guest = plugin_module.VideoFetchNiconicoIE(v4_responses=[make_v4_data()])
v4_guest_result = v4_guest._real_extract("https://www.nicovideo.jp/watch/sm37593092")
assert v4_guest_result["videofetch_niconico_api_mode"] == "v4-preissued-dms"
assert [f["height"] for f in v4_guest_result["formats"]] == [1080, 720]
v4_url, v4_request = v4_guest.v4_requests[0]
assert v4_url.endswith("/v4/watch/sm37593092")
assert v4_request["headers"]["Cookie"] == ""
assert v4_request["headers"]["X-Request-With"] == "https://www.nicovideo.jp"
body = json.loads(v4_request["data"].decode())
assert body["asGuest"] is True
assert re.fullmatch(r"[A-Za-z0-9]{10}_\d{13}", body["actionTrackId"])
assert v4_guest.m3u8_requests[0][1]["headers"]["Cookie"] == "domand_bid=fresh-domand-bid"
assert v4_guest_result["title"] == "Niconico v4 fixture"

# When v4 returns no pre-issued HLS, use the watch response actionTrackId and
# create an access-rights session with only streams actually marked available.
v4_rights = plugin_module.VideoFetchNiconicoIE(
    v4_responses=[make_v4_data(preissued=False, guest=True)])
v4_rights_result = v4_rights._real_extract("https://www.nicovideo.jp/watch/sm37593092")
assert v4_rights_result["videofetch_niconico_api_mode"] == "v4-access-rights-dms"
rights_url, rights_request = v4_rights.access_request
assert rights_url.endswith("actionTrackId=WatchTrack1_1700000000000")
assert rights_request["headers"]["Cookie"] == ""
assert rights_request["headers"]["X-Access-Right-Key"] == "fake-v4-access-key"
assert json.loads(rights_request["data"].decode()) == {
    "outputs": [
        ["video-h264-1080p", "audio-aac-192kbps"],
        ["video-h264-720p", "audio-aac-192kbps"],
    ]
}
assert v4_rights.m3u8_requests[0][1]["headers"]["Cookie"] == "domand_bid=rights-domand-bid"

# A configured session can still receive guest data. The response's viewer,
# not the presence of the saved cookie, determines whether access-rights is anonymous.
expired_session = plugin_module.VideoFetchNiconicoIE(
    v4_responses=[make_v4_data(preissued=False, guest=True)],
    logged_in=True)
expired_session._real_extract("https://www.nicovideo.jp/watch/sm37593092")
assert expired_session.v4_requests[0][1]["headers"].get("Cookie") is None
assert expired_session.access_request[1]["headers"]["Cookie"] == ""

# A live signed-in v4 result uses the saved session on the watch/access-rights call,
# but the media CDN receives only its short-lived domand_bid binding cookie.
signed_in = plugin_module.VideoFetchNiconicoIE(
    v4_responses=[make_v4_data(preissued=False, guest=False)],
    logged_in=True)
signed_in._real_extract("https://www.nicovideo.jp/watch/sm37593092")
assert signed_in.v4_requests[0][1]["headers"].get("Cookie") is None
assert "Cookie" not in signed_in.access_request[1]["headers"]
assert signed_in.m3u8_requests[0][1]["headers"]["Cookie"] == "domand_bid=rights-domand-bid"

# Legacy v3 guest fallback also suppresses an app-saved session cookie.
legacy_data = {
    "video": {"id": "sm37593092", "title": "legacy", "duration": 42},
    "client": {"watchTrackId": "LegacyTrack_1700000000000"},
    "media": {
        "contents": {
            "videos": [{"id": "video-720", "isAvailable": True}],
            "audios": [{"id": "audio-high", "isAvailable": True}],
        },
        "accessRightKey": "legacy-key",
    },
}
legacy_response = {"meta": {"status": 200}, "data": legacy_data}
legacy = plugin_module.VideoFetchNiconicoIE(
    v4_responses=[ExtractorError("v4 not available")],
    api_responses=[legacy_response])
legacy_result = legacy._real_extract("https://www.nicovideo.jp/watch/sm37593092")
assert legacy_result["videofetch_niconico_api_mode"] == "v3_guest-dms"
assert len(legacy.api_requests) == 1
assert legacy.api_requests[0][1]["headers"]["Cookie"] == ""
assert legacy.access_request[1]["headers"]["Cookie"] == ""
assert legacy.access_request[0].endswith("actionTrackId=LegacyTrack_1700000000000")

print("Niconico NVAPI v4 / pre-issued HLS / guest isolation / v3 fallback tests passed")
