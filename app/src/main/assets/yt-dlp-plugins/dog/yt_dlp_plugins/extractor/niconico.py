"""Dog's Niconico watch-page / DMS extractor.

Niconico started moving the watch payload under $watchV4 in October 2026.
The normal yt-dlp extractor still requests v3 or v3_guest. This plugin first
uses the server-response payload embedded in the public watch page, selects
only streams marked isAvailable, and asks DMS for the HLS manifest. If that
route is not present or fails, it delegates to yt-dlp's upstream extractor.

No account state is fabricated: the page request uses only cookies already in
the local yt-dlp cookie jar, and DMS decides which stream IDs are available.
"""

import html
import itertools
import json
import re
from urllib.parse import urlencode
from urllib.request import Request

from yt_dlp.extractor.niconico import NiconicoIE as _NiconicoIE
from yt_dlp.utils import ExtractorError, extract_attributes, parse_iso8601


class DogNiconicoIE(_NiconicoIE, plugin_name='dog_niconico_watch_v4_dms'):
    _DMS_API_HEADERS = {
        'Accept': 'application/json;charset=utf-8',
        'Content-Type': 'application/json',
        'X-Frontend-Id': '6',
        'X-Frontend-Version': '0',
        'X-Niconico-Language': 'ja-jp',
        'X-Request-With': 'nicovideo',
    }

    def _get_watch_response(self, url, video_id):
        webpage = self._download_webpage(
            url, video_id, note='Downloading Niconico watch-page metadata')
        for tag in re.findall(r'<meta\b[^>]*>', webpage, flags=re.I):
            attrs = extract_attributes(tag)
            if attrs.get('name', '').lower() != 'server-response':
                continue
            raw_content = attrs.get('content')
            if not raw_content:
                break
            payload = json.loads(html.unescape(raw_content))
            response = (payload.get('data') or {}).get('response') or payload
            watch_v4 = response.get('$watchV4')
            if isinstance(watch_v4, dict):
                data = watch_v4.get('data')
                if isinstance(data, dict):
                    return data, 'watchV4-dms'
            return response, 'server-response-dms'
        return None, ''

    @staticmethod
    def _get_dms_streams(media):
        # Current watch V4 payload uses media.contents; some older watch
        # responses expose the same DMS data under media.domand.
        contents = media.get('contents') if isinstance(media, dict) else None
        domand = media.get('domand') if isinstance(media, dict) else None
        source = contents if isinstance(contents, dict) else domand
        if not isinstance(source, dict):
            return None

        videos = [
            item for item in (source.get('videos') or [])
            if isinstance(item, dict) and item.get('id') and item.get('isAvailable')
        ]
        audios = [
            item for item in (source.get('audios') or [])
            if isinstance(item, dict) and item.get('id') and item.get('isAvailable')
        ]
        access_key = source.get('accessRightKey') or media.get('accessRightKey')
        if not access_key and isinstance(domand, dict):
            access_key = domand.get('accessRightKey')
        return (videos, audios, access_key) if videos and audios and access_key else None

    def _extract_watch_page_dms(self, url, video_id):
        watch_data, route = self._get_watch_response(url, video_id)
        if not isinstance(watch_data, dict):
            return None

        media = watch_data.get('media') or {}
        stream_data = self._get_dms_streams(media)
        if not stream_data:
            self.write_debug('Dog Niconico: watch-page payload has no available DMS video/audio pair')
            return None

        videos, audios, access_key = stream_data
        client = watch_data.get('client') or {}
        watch_track_id = client.get('watchTrackId')
        video = watch_data.get('video') or {}
        actual_video_id = video.get('id') or video_id
        if not watch_track_id:
            self.write_debug('Dog Niconico: watch-page payload has no watchTrackId')
            return None

        # Preserve all stream combinations returned as available by Niconico.
        # The same actionTrackId belongs to the accessRightKey from this page.
        outputs = [[video_item['id'], audio_item['id']]
                   for video_item, audio_item in itertools.product(videos, audios)]
        endpoint = (
            f'https://nvapi.nicovideo.jp/v1/watch/{actual_video_id}/access-rights/hls'
            f'?{urlencode({"actionTrackId": watch_track_id})}'
        )
        headers = {
            **self._DMS_API_HEADERS,
            'X-Access-Right-Key': access_key,
        }
        payload = json.dumps({'outputs': outputs}).encode()

        # Match the browser flow's preflight before the DMS access-rights POST.
        try:
            self._request_webpage(
                Request(endpoint, headers=headers, method='OPTIONS'),
                video_id, note='Checking Niconico DMS access rights')
        except Exception as error:
            self.write_debug(f'Dog Niconico: DMS OPTIONS preflight was not accepted: {error}')

        access = self._download_json(
            endpoint, video_id, note='Requesting Niconico DMS HLS access rights',
            headers=headers, data=payload)
        manifest_url = (((access or {}).get('data') or {}).get('contentUrl'))
        if not manifest_url:
            raise ExtractorError('Niconico DMS did not return an HLS content URL')

        formats = self._extract_m3u8_formats(
            manifest_url, video_id, 'mp4', m3u8_id='dms', fatal=True)
        if not formats:
            raise ExtractorError('Niconico DMS returned an empty HLS manifest')

        owner = watch_data.get('owner') or watch_data.get('channel') or {}
        thumbnail_data = video.get('thumbnail') or {}
        thumbnail = (
            thumbnail_data.get('ogp') or thumbnail_data.get('player')
            or thumbnail_data.get('large') or thumbnail_data.get('middle')
            or thumbnail_data.get('normal')
        )
        counts = video.get('count') or {}
        result = {
            'id': actual_video_id,
            'title': video.get('title') or 'Niconico video',
            'description': video.get('description'),
            'duration': video.get('duration'),
            'timestamp': parse_iso8601(video.get('registeredAt')),
            'uploader': owner.get('nickname') or owner.get('name'),
            'uploader_id': owner.get('id'),
            'thumbnail': thumbnail,
            'view_count': counts.get('view'),
            'like_count': counts.get('like'),
            'comment_count': counts.get('comment'),
            'formats': formats,
            'dog_niconico_api_mode': route,
        }
        self.write_debug(
            f'Dog Niconico: {route} DMS route returned {len(formats)} HLS formats '
            f'from {len(videos)} available video and {len(audios)} available audio source(s)')
        return result

    def _real_extract(self, url):
        video_id = self._match_id(url)
        try:
            result = self._extract_watch_page_dms(url, video_id)
            if result:
                return result
        except Exception as error:
            # Fall back to upstream v3/v3_guest for older pages or videos that
            # do not expose DMS media in the watch-page response.
            self.write_debug(
                f'Dog Niconico: watch-page DMS extraction was unavailable; '
                f'falling back to yt-dlp upstream extractor ({error})')

        result = super()._real_extract(url)
        result['dog_niconico_api_mode'] = 'v3' if super().is_logged_in else 'v3_guest'
        return result
