"""VideoFetch Douyin extractor.

Supports Douyin share URLs and uses the mobile share-page SSR payload as a
fallback when the upstream web-detail endpoint returns an empty response.
No private API signatures, fabricated cookies, or third-party parsing services.
"""
import html
import json
import re
from urllib.parse import parse_qs, unquote, urlparse

from yt_dlp.extractor.tiktok import DouyinIE
from yt_dlp.utils import ExtractorError


class VideoFetchDouyinIE(DouyinIE, plugin_name='videofetch_douyin'):
    IE_NAME = 'videofetch:douyin'
    _VALID_URL = (
        r'https?://(?:(?:www|m)\.)?douyin\.com/(?:video|share/video)/(?P<id>\d+)'
        r'/?(?:[?#].*)?$'
        r'|https?://v\.douyin\.com/(?P<share>[\w-]+)/?(?:[?#].*)?$'
        r'|https?://(?:www\.)?iesdouyin\.com/share/video/(?P<id2>\d+)'
        r'/?(?:[?#].*)?$'
    )
    _MOBILE_HEADERS = {
        'User-Agent': (
            'Mozilla/5.0 (Linux; Android 8.0.0; SM-G955U Build/R16NW) '
            'AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 '
            'Mobile Safari/537.36'
        ),
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
        'Referer': 'https://www.douyin.com/?is_from_mobile_home=1&recommend=1',
    }

    @staticmethod
    def _video_id_from_url(url):
        parsed = urlparse(url)
        match = re.search(r'/(?:video|share/video)/(\d+)', parsed.path)
        if match:
            return match.group(1)
        query = parse_qs(parsed.query)
        for key in ('aweme_id', 'item_id', 'modal_id'):
            values = query.get(key)
            if values and re.fullmatch(r'\d+', values[0]):
                return values[0]
        return None

    def _resolve_video_id(self, url):
        match = self._match_valid_url(url)
        video_id = match.group('id') or match.group('id2')
        if video_id:
            return video_id

        share_code = match.group('share')
        _, handle = self._download_webpage_handle(
            url, share_code, note='Resolving Douyin share link',
            headers=self._MOBILE_HEADERS)
        final_url = handle.geturl() if handle else url
        video_id = self._video_id_from_url(final_url)
        if video_id:
            return video_id

        raise ExtractorError(
            'Douyin share link did not redirect to a recognizable public video page',
            expected=True)

    @staticmethod
    def _json_object_after(text, marker):
        """Extract a balanced JSON object following a JS assignment marker."""
        marker_pos = text.find(marker)
        if marker_pos < 0:
            return None
        start = text.find('{', marker_pos + len(marker))
        if start < 0:
            return None

        depth = 0
        in_string = False
        escaped = False
        for pos in range(start, len(text)):
            char = text[pos]
            if in_string:
                if escaped:
                    escaped = False
                elif char == '\\':
                    escaped = True
                elif char == '"':
                    in_string = False
                continue
            if char == '"':
                in_string = True
            elif char == '{':
                depth += 1
            elif char == '}':
                depth -= 1
                if depth == 0:
                    try:
                        return json.loads(text[start:pos + 1])
                    except (ValueError, TypeError):
                        return None
        return None

    @staticmethod
    def _find_aweme_detail(data, video_id):
        """Find the SSR video object in nested Douyin route/rehydration data."""
        if isinstance(data, dict):
            video_info = data.get('videoInfoRes')
            if isinstance(video_info, dict):
                items = video_info.get('item_list') or video_info.get('itemList') or []
                for item in items:
                    if isinstance(item, dict):
                        item_id = str(item.get('aweme_id') or item.get('id') or '')
                        if not item_id or item_id == video_id:
                            return item

            for key in ('aweme_detail', 'itemStruct'):
                detail = data.get(key)
                if isinstance(detail, dict):
                    detail_id = str(detail.get('aweme_id') or detail.get('id') or '')
                    if not detail_id or detail_id == video_id:
                        return detail

            detail_id = str(data.get('aweme_id') or data.get('awemeId') or data.get('id') or '')
            if (isinstance(data.get('video'), dict)
                    and (not detail_id or detail_id == video_id)
                    and ('desc' in data or 'author' in data)):
                return data

            for value in data.values():
                if isinstance(value, (dict, list)):
                    found = VideoFetchDouyinIE._find_aweme_detail(value, video_id)
                    if found:
                        return found

        elif isinstance(data, list):
            for value in data:
                if isinstance(value, (dict, list)):
                    found = VideoFetchDouyinIE._find_aweme_detail(value, video_id)
                    if found:
                        return found
        return None

    def _extract_ssr_detail(self, webpage, video_id):
        if not webpage:
            return None

        # Modern Douyin mobile share pages expose videoInfoRes in _ROUTER_DATA.
        for marker in ('window._ROUTER_DATA', 'window._SSR_DATA', 'window.__INIT_PROPS__'):
            payload = self._json_object_after(webpage, marker)
            if payload:
                detail = self._find_aweme_detail(payload, video_id)
                if detail:
                    return detail

        # Older deployments use script-tag JSON payloads.
        for script_id in ('__UNIVERSAL_DATA_FOR_REHYDRATION__', 'RENDER_DATA'):
            pattern = (
                r'<script\b[^>]*\bid=["\']' + re.escape(script_id)
                + r'["\'][^>]*>(.*?)</script>'
            )
            match = re.search(pattern, webpage, flags=re.I | re.S)
            if not match:
                continue
            content = html.unescape(match.group(1)).strip()
            if script_id == 'RENDER_DATA':
                content = unquote(content)
            try:
                payload = json.loads(content)
            except (ValueError, TypeError):
                continue
            detail = self._find_aweme_detail(payload, video_id)
            if detail:
                return detail
        return None

    @staticmethod
    def _add_download_headers(info):
        if not isinstance(info, dict):
            return info
        headers = {
            'Referer': 'https://www.douyin.com/',
            'User-Agent': VideoFetchDouyinIE._MOBILE_HEADERS['User-Agent'],
        }
        for fmt in info.get('formats') or []:
            if not isinstance(fmt, dict):
                continue
            stream_url = fmt.get('url')
            if isinstance(stream_url, str) and '/playwm/' in stream_url:
                fmt['url'] = stream_url.replace('/playwm/', '/play/')
            existing_headers = fmt.get('http_headers')
            if not isinstance(existing_headers, dict):
                existing_headers = {}
            fmt['http_headers'] = {**existing_headers, **headers}
        return info

    def _real_extract(self, url):
        video_id = self._resolve_video_id(url)
        canonical_url = f'https://www.douyin.com/video/{video_id}'
        share_page = f'https://www.iesdouyin.com/share/video/{video_id}/'

        # Prefer the SSR share page: the PC detail endpoint can return an empty
        # body even with cookies, while the mobile share page may embed item data.
        try:
            webpage = self._download_webpage(
                share_page, video_id, note='Downloading Douyin mobile share page',
                headers=self._MOBILE_HEADERS, fatal=False)
            detail = self._extract_ssr_detail(webpage, video_id)
            if detail:
                info = self._parse_aweme_video_app(detail)
                if info.get('formats'):
                    self.write_debug(
                        f'VideoFetch Douyin: extracted {len(info["formats"])} formats from share-page SSR')
                    return self._add_download_headers(info)
        except Exception as error:
            self.write_debug(f'VideoFetch Douyin: share-page SSR route unavailable ({error})')

        # Keep upstream's maintained API path as a fallback.
        try:
            info = super()._real_extract(canonical_url)
            return self._add_download_headers(info)
        except Exception as error:
            raise ExtractorError(
                'Douyin metadata could not be extracted from the mobile share page or yt-dlp API. '
                f'The post may be private, deleted, blocked by a verification challenge, or unavailable on this network. '
                f'Upstream error: {error}',
                expected=True)
