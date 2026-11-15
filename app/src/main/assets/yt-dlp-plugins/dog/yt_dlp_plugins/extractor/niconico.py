"""Dog's Niconico extractor.

The browser downloader pattern used as a reference requests the standard v3
watch API first with browser/session credentials, then retries v3_guest if
the standard request is rejected. Both paths resolve DMS streams and use the
real isAvailable flags. Dog's cookie jar is passed by yt-dlp to these requests;
no session or Premium entitlement is fabricated.

For newer watch-page deployments, the $watchV4 server-response payload is a
secondary path. The upstream yt-dlp extractor remains the final fallback.
"""

import html
import itertools
import json
import re
import time
from urllib.parse import urlencode
from urllib.request import Request

from yt_dlp.extractor.niconico import NiconicoIE as _NiconicoIE
from yt_dlp.utils import ExtractorError, extract_attributes, parse_iso8601


class DogNiconicoIE(_NiconicoIE, plugin_name='dog_niconico_v4_dms'):
    _API_HEADERS = {
        'Accept': 'application/json, text/plain, */*',
        'X-Frontend-Id': '6',
        'X-Frontend-Version': '0',
        'X-Niconico-Language': 'ja-jp',
    }
    _DMS_API_HEADERS = {
        'Accept': 'application/json;charset=utf-8',
        'Content-Type': 'application/json',
        'X-Frontend-Id': '6',
        'X-Frontend-Version': '0',
        'X-Niconico-Language': 'ja-jp',
        'X-Request-With': 'https://www.nicovideo.jp',
    }

    def _make_metadata(self, watch_data, video_id, route, formats):
        video = watch_data.get('video') or {}
        owner = watch_data.get('owner') or watch_data.get('channel') or {}
        thumbnail_data = video.get('thumbnail') or {}
        thumbnail = (
            thumbnail_data.get('ogp') or thumbnail_data.get('player')
            or thumbnail_data.get('large') or thumbnail_data.get('middle')
            or thumbnail_data.get('normal')
        )
        counts = video.get('count') or {}
        return {
            'id': video.get('id') or video_id,
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

    def _get_dms_streams(self, media):
        if not isinstance(media, dict):
            return None
        contents = media.get('contents')
        domand = media.get('domand')
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
        access_key = (
            source.get('accessRightKey')
            or media.get('accessRightKey')
            or (domand.get('accessRightKey') if isinstance(domand, dict) else None)
        )
        return (videos, audios, access_key) if videos and audios and access_key else None

    def _request_dms_formats(self, watch_data, video_id):
        # Prefer yt-dlp's own domand implementation. It already maps audio/video
        # HLS variants, codecs, bitrate and source IDs into format entries.
        media = watch_data.get('media') or {}
        domand = media.get('domand') if isinstance(media, dict) else None
        if isinstance(domand, dict):
            formats = self._extract_formats(watch_data, video_id)
            if formats:
                return formats

        stream_data = self._get_dms_streams(media)
        if not stream_data:
            return None

        videos, audios, access_key = stream_data
        client = watch_data.get('client') or {}
        watch_track_id = client.get('watchTrackId')
        if not watch_track_id:
            return None

        outputs = [
            [video_item['id'], audio_item['id']]
            for video_item, audio_item in itertools.product(videos, audios)
        ]
        endpoint = (
            f'https://nvapi.nicovideo.jp/v1/watch/{video_id}/access-rights/hls'
            f'?{urlencode({"actionTrackId": watch_track_id})}'
        )
        headers = {
            **self._DMS_API_HEADERS,
            'X-Access-Right-Key': access_key,
        }
        payload = json.dumps({'outputs': outputs}).encode()

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
            return None

        return self._extract_m3u8_formats(
            manifest_url, video_id, 'mp4', m3u8_id='dms', fatal=True)

    def _extract_v3_api(self, video_id, guest, action_track_id):
        path = 'v3_guest' if guest else 'v3'
        endpoint = f'https://www.nicovideo.jp/api/watch/{path}/{video_id}'
        query = {
            '_frontendId': '6',
            '_frontendVersion': '0',
            'actionTrackId': action_track_id,
            'skips': 'harmful',
            'noSideEffect': 'false',
            't': action_track_id.rsplit('_', 1)[-1],
        }
        response = self._download_json(
            endpoint, video_id,
            note=f'Requesting Niconico {path} watch API',
            headers=self._API_HEADERS,
            query=query,
            expected_status=[400, 404],
        )
        meta = (response or {}).get('meta') or {}
        if meta.get('status') != 200:
            raise ExtractorError(
                f'Niconico {path} API returned status {meta.get("status")}: '
                f'{meta.get("errorCode", "unknown error")}',
                expected=True)
        watch_data = (response or {}).get('data') or {}
        formats = self._request_dms_formats(watch_data, video_id)
        if not formats:
            raise ExtractorError(
                f'Niconico {path} API returned no available DMS formats',
                expected=True)
        route = 'v3_guest-dms' if guest else 'v3-dms'
        self.write_debug(
            f'Dog Niconico: {path} API returned {len(formats)} DMS HLS formats')
        return self._make_metadata(watch_data, video_id, route, formats)

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

    def _extract_watch_page_dms(self, url, video_id):
        watch_data, route = self._get_watch_response(url, video_id)
        if not isinstance(watch_data, dict):
            return None
        formats = self._request_dms_formats(watch_data, video_id)
        if not formats:
            return None
        self.write_debug(f'Dog Niconico: {route} returned {len(formats)} DMS HLS formats')
        return self._make_metadata(watch_data, video_id, route, formats)

    def _real_extract(self, url):
        video_id = self._match_id(url)
        action_track_id = f'AAAAAAAAAA_{int(time.time() * 1000)}'

        # Match the browser reference flow: standard v3 first, then guest.
        # yt-dlp's cookie jar is automatically attached to both requests.
        for guest in (False, True):
            try:
                return self._extract_v3_api(video_id, guest, action_track_id)
            except Exception as error:
                route = 'v3_guest' if guest else 'v3'
                self.write_debug(
                    f'Dog Niconico: {route} DMS route unavailable; '
                    f'{"no further v3 fallback" if guest else "trying v3_guest"} ({error})')

        # Watch V4 can be exposed in the HTML server-response even when the
        # legacy v3 JSON endpoints are unavailable.
        try:
            result = self._extract_watch_page_dms(url, video_id)
            if result:
                return result
        except Exception as error:
            self.write_debug(
                f'Dog Niconico: watch-page V4/DMS route unavailable ({error})')

        # Final compatibility fallback for older or non-DMS videos.
        result = super()._real_extract(url)
        result['dog_niconico_api_mode'] = (
            'v3' if super().is_logged_in else 'v3_guest')
        return result
