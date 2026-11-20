"""VideoFetch Kuaishou extractor for public share links and video pages.

Uses the public share redirect and the page's server-rendered Apollo state.
No private API signatures, fabricated cookies, or account state are generated.
"""
import json
import re
from urllib.parse import urlparse

from yt_dlp.extractor.common import InfoExtractor
from yt_dlp.utils import ExtractorError


class VideoFetchKuaishouIE(InfoExtractor):
    IE_NAME = 'videofetch:kuaishou'
    _VALID_URL = (
        r'https?://(?:(?:www\.)?kuaishou\.com/(?:short-video|f)/'
        r'(?P<id>[0-9A-Za-z]+)|v\.kuaishou\.com/(?P<share>[0-9A-Za-z]+)'
        r'|(?:[a-z0-9-]+\.)?m\.chenzhongtech\.com/fw/photo/(?P<mobile>[0-9A-Za-z]+))'
        r'/?(?:[?#].*)?$'
    )
    _HEADERS = {
        'User-Agent': 'Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36',
        'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
        'Referer': 'https://www.kuaishou.com/',
    }
    _IIFE_TAIL = ';(function(){var s;(s=document.currentScript||document.scripts[document.scripts.length-1]).parentNode.removeChild(s);}());'

    def _resolve_share(self, url, video_id):
        webpage, handle = self._download_webpage_handle(
            url, video_id, note='Resolving Kuaishou share link',
            headers={k: v for k, v in self._HEADERS.items() if k != 'Referer'})
        final_url = handle.geturl() if handle else url
        parsed = urlparse(final_url)
        match = re.search(r'/(?:short-video|fw/photo)/([0-9A-Za-z]+)', parsed.path)
        if match:
            return match.group(1)
        query = re.search(r'[?&]photoId=([0-9A-Za-z]+)', final_url)
        if query:
            return query.group(1)
        raise ExtractorError(
            'Kuaishou share link did not redirect to a recognizable public video page',
            expected=True)

    @staticmethod
    def _resolve_ref(value, client):
        for _ in range(12):
            if not isinstance(value, dict) or value.get('type') != 'id':
                break
            next_value = client.get(value.get('id'))
            if not isinstance(next_value, dict):
                return next_value
            value = next_value
        return value

    def _extract_apollo_photo(self, webpage, video_id):
        marker = re.search(r'window\.__APOLLO_STATE__\s*=\s*', webpage)
        if not marker:
            return None, None
        raw = webpage[marker.end():]
        script_end = raw.find('</script>')
        if script_end >= 0:
            raw = raw[:script_end]
        raw = raw.replace(self._IIFE_TAIL, '').strip().rstrip(';')
        try:
            state = json.loads(raw)
        except (ValueError, TypeError):
            return None, None
        client = state.get('defaultClient') or {}
        photo = client.get('VisionVideoDetailPhoto:' + video_id)
        if not isinstance(photo, dict):
            # Some page revisions include the ID as a JSON-quoted key.
            for key, value in client.items():
                if key.startswith('VisionVideoDetailPhoto:') and isinstance(value, dict):
                    if key.rsplit(':', 1)[-1] == video_id:
                        photo = value
                        break
        return (photo if isinstance(photo, dict) else None), client

    def _video_formats(self, photo, client, video_id):
        candidates = []

        def add_manifest(manifest):
            manifest = self._resolve_ref(manifest, client)
            if not isinstance(manifest, dict):
                return
            for adaptation in manifest.get('adaptationSet') or []:
                adaptation = self._resolve_ref(adaptation, client)
                if not isinstance(adaptation, dict):
                    continue
                for representation in adaptation.get('representation') or []:
                    representation = self._resolve_ref(representation, client)
                    if not isinstance(representation, dict):
                        continue
                    urls = []
                    for key in ('url', 'backupUrl'):
                        value = representation.get(key)
                        if isinstance(value, dict):
                            value = value.get('json')
                        if isinstance(value, str):
                            urls.append(value)
                        elif isinstance(value, list):
                            urls.extend(item for item in value if isinstance(item, str))
                        if urls:
                            break
                    for stream_url in urls:
                        if stream_url.startswith(('https://', 'http://')):
                            candidates.append({
                                'url': stream_url,
                                'height': int(representation.get('height') or 0),
                                'tbr': (representation.get('avgBitrate') or 0) / 1000,
                                'ext': 'mp4',
                                'format_id': f'kuaishou-{representation.get("height") or "source"}',
                                'http_headers': self._HEADERS,
                            })

        add_manifest(photo.get('manifest'))
        h265 = photo.get('manifestH265')
        if isinstance(h265, dict) and 'json' in h265:
            h265 = h265.get('json')
        add_manifest(h265)

        for key in ('photoUrl', 'photoH265Url'):
            stream_url = photo.get(key)
            if isinstance(stream_url, str) and stream_url.startswith(('https://', 'http://')):
                candidates.append({
                    'url': stream_url, 'ext': 'mp4',
                    'format_id': 'kuaishou-' + key,
                    'http_headers': self._HEADERS,
                })

        unique = {}
        for item in candidates:
            unique.setdefault(item['url'], item)
        return sorted(unique.values(), key=lambda item: (
            item.get('height') or 0, item.get('tbr') or 0), reverse=True)

    def _real_extract(self, url):
        match = self._match_valid_url(url)
        video_id = match.group('id') or match.group('mobile')
        if not video_id:
            video_id = self._resolve_share(url, match.group('share'))

        page_url = f'https://www.kuaishou.com/short-video/{video_id}'
        webpage = self._download_webpage(
            page_url, video_id, note='Downloading Kuaishou public video page',
            headers=self._HEADERS)
        photo, client = self._extract_apollo_photo(webpage, video_id)
        if not photo:
            raise ExtractorError(
                'Kuaishou did not expose public video metadata. The work may be private, deleted, region-restricted, or temporarily blocked.',
                expected=True)

        formats = self._video_formats(photo, client, video_id)
        if not formats:
            raise ExtractorError(
                'Kuaishou page was recognized, but no downloadable video stream was exposed (the post may be an image album or restricted).',
                expected=True)

        author = photo.get('_authorName') or photo.get('userName')
        return {
            'id': video_id,
            'title': photo.get('caption') or f'Kuaishou video {video_id}',
            'uploader': author,
            'thumbnail': photo.get('coverUrl') or photo.get('cover'),
            'duration': photo.get('duration'),
            'formats': formats,
            'http_headers': self._HEADERS,
        }
