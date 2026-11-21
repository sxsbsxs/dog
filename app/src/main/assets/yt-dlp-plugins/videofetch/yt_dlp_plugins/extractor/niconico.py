"""VideoFetch's Niconico extractor.

The current web player uses NVAPI v4. Prefer its watch response and pre-issued
DMS/HLS session, then use the v1 access-rights endpoint if needed. Older v3
watch APIs and yt-dlp's upstream extractor remain compatibility fallbacks.
"""

import html
import itertools
import json
import re
import secrets
import string
import time
from urllib.parse import urlencode

from yt_dlp.extractor.niconico import NiconicoIE as _NiconicoIE
from yt_dlp.utils import ExtractorError, extract_attributes, parse_iso8601


class VideoFetchNiconicoIE(_NiconicoIE, plugin_name='videofetch_niconico_v4_dms'):
    _FRONTEND_HEADERS = {
        'Accept': 'application/json, text/plain, */*',
        'X-Frontend-Id': '6',
        'X-Frontend-Version': '0',
        'X-Niconico-Language': 'ja-jp',
    }
    _V4_HEADERS = {
        **_FRONTEND_HEADERS,
        'X-Request-With': 'https://www.nicovideo.jp',
        'Content-Type': 'application/json',
    }
    _DMS_HEADERS = {
        **_FRONTEND_HEADERS,
        'Accept': 'application/json;charset=utf-8',
        'Content-Type': 'application/json',
        'X-Request-With': 'https://www.nicovideo.jp',
    }
    _NVAPI = 'https://nvapi.nicovideo.jp'
    _NICONICO_ORIGIN = 'https://www.nicovideo.jp'

    @staticmethod
    def _new_action_track_id():
        token = ''.join(secrets.choice(string.ascii_letters + string.digits) for _ in range(10))
        return f'{token}_{int(time.time() * 1000)}'

    def _get_domand_bid(self):
        # The v4 watch and access-rights responses issue domand_bid as a
        # Set-Cookie header. Read it from yt-dlp's cookie jar; never log it.
        for url in (
            'https://delivery.domand.nicovideo.jp/',
            'https://nvapi.nicovideo.jp/',
            'https://www.nicovideo.jp/',
        ):
            try:
                cookie = self._get_cookies(url).get('domand_bid')
                value = getattr(cookie, 'value', None) if cookie else None
                if value:
                    return value
            except Exception:
                pass
        return None

    def _media_request_headers(self):
        headers = {
            'Origin': self._NICONICO_ORIGIN,
            'Referer': self._NICONICO_ORIGIN + '/',
        }
        bid = self._get_domand_bid()
        # The DMS CDN requires domand_bid. Do not forward user_session to the
        # media CDN: a guest key and a signed-in cookie must never be mixed.
        headers['Cookie'] = f'domand_bid={bid}' if bid else ''
        return headers

    def _extract_dms_hls_formats(self, manifest_url, video_id, m3u8_id, fatal=True):
        if not manifest_url:
            return []
        return self._extract_m3u8_formats(
            manifest_url, video_id, 'mp4',
            m3u8_id=m3u8_id,
            headers=self._media_request_headers(),
            fatal=fatal,
        )

    def _make_metadata(self, watch_data, video_id, route, formats):
        video = watch_data.get('video') or {}
        owner = (
            watch_data.get('owner')
            or watch_data.get('channel')
            or ((watch_data.get('metadata') or {}).get('jsonLd') or {}).get('owner')
            or {}
        )
        thumbnail_data = video.get('thumbnail') or {}
        thumbnail = (
            thumbnail_data.get('ogp') or thumbnail_data.get('player')
            or thumbnail_data.get('large') or thumbnail_data.get('middle')
            or thumbnail_data.get('normal') or thumbnail_data.get('url')
        )
        counts = video.get('count') or {}
        owner_name = owner.get('nickname') or owner.get('name')
        return {
            'id': video.get('id') or video_id,
            'title': video.get('title') or 'Niconico video',
            'description': video.get('description'),
            'duration': video.get('duration'),
            'timestamp': parse_iso8601(video.get('registeredAt')),
            'uploader': owner_name,
            'uploader_id': owner.get('id'),
            'thumbnail': thumbnail,
            'view_count': counts.get('view'),
            'like_count': counts.get('like'),
            'comment_count': counts.get('comment'),
            'formats': formats,
            'videofetch_niconico_api_mode': route,
        }

    def _get_dms_streams(self, media):
        if not isinstance(media, dict):
            return None

        domand = media.get('domand')
        # v4 uses media.contents; v3 usually uses media.domand. Some responses
        # carry both, with one container empty, so inspect both independently.
        for source in (media.get('contents'), domand):
            if not isinstance(source, dict):
                continue

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
            # Audio-only videos are valid DMS outputs; video tracks are optional.
            if audios and access_key:
                return videos, audios, access_key
        return None

    def _request_dms_formats(self, watch_data, video_id, guest=False, action_track_id=None):
        media = watch_data.get('media') or {}
        domand = media.get('domand') if isinstance(media, dict) else None

        stream_data = self._get_dms_streams(media)
        if not stream_data:
            return None

        videos, audios, access_key = stream_data
        client = watch_data.get('client') or {}
        watch_track_id = (
            action_track_id
            or client.get('watchTrackId')
            or self._new_action_track_id()
        )
        if videos:
            outputs = [
                [video_item['id'], audio_item['id']]
                for video_item, audio_item in itertools.product(videos, audios)
            ]
        else:
            outputs = [[audio_item['id']] for audio_item in audios]

        endpoint = (
            f'{self._NVAPI}/v1/watch/{video_id}/access-rights/hls'
            f'?{urlencode({"actionTrackId": watch_track_id})}'
        )
        headers = {
            **self._DMS_HEADERS,
            'X-Access-Right-Key': access_key,
        }
        if guest:
            # A guest-issued accessRightKey combined with user_session returns
            # 400 INVALID_PARAMETER. An empty Cookie header prevents cookiejar
            # injection even when the app has a saved but expired session.
            headers['Cookie'] = ''
        payload = json.dumps({'outputs': outputs}).encode()
        access = self._download_json(
            endpoint, video_id,
            note='Requesting Niconico DMS HLS access rights',
            headers=headers,
            data=payload,
        )
        meta_status = ((access or {}).get('meta') or {}).get('status')
        if meta_status not in (200, 201):
            error_code = ((access or {}).get('meta') or {}).get('errorCode', 'unknown error')
            raise ExtractorError(
                f'Niconico DMS access-rights returned status {meta_status}: {error_code}',
                expected=True)
        manifest_url = (((access or {}).get('data') or {}).get('contentUrl'))
        if not manifest_url:
            return None

        return self._extract_dms_hls_formats(
            manifest_url, video_id, 'dms-access-rights', fatal=True)

    def _extract_v4_api(self, video_id):
        logged_in = bool(super().is_logged_in)
        action_track_id = self._new_action_track_id()
        body = {'actionTrackId': action_track_id}
        headers = dict(self._V4_HEADERS)
        if not logged_in:
            body['asGuest'] = True
            headers['Cookie'] = ''

        response = self._download_json(
            f'{self._NVAPI}/v4/watch/{video_id}', video_id,
            note='Requesting Niconico NVAPI v4 watch data',
            headers=headers,
            data=json.dumps(body).encode(),
            expected_status=[400, 404],
        )
        meta = (response or {}).get('meta') or {}
        if meta.get('status') != 200:
            raise ExtractorError(
                f'Niconico v4 watch API returned status {meta.get("status")}: '
                f'{meta.get("errorCode", "unknown error")}',
                expected=True)

        watch_data = (response or {}).get('data') or {}
        if not isinstance(watch_data.get('video'), dict):
            raise ExtractorError('Niconico v4 watch API returned no video metadata', expected=True)

        # Do not infer this from whether a cookie exists: a stale user_session
        # may still receive a successful guest response (viewer == null).
        guest = watch_data.get('viewer') is None
        if guest and logged_in:
            self.write_debug('VideoFetch Niconico: v4 returned guest data for the saved session')
        client = watch_data.get('client') or {}
        watch_track_id = client.get('watchTrackId') or action_track_id
        media = watch_data.get('media')

        formats = []
        hls = media.get('hls') if isinstance(media, dict) else None
        preissued_url = hls.get('url') if isinstance(hls, dict) else None
        if preissued_url and self._get_domand_bid():
            try:
                formats = self._extract_dms_hls_formats(
                    preissued_url, video_id, 'dms-v4-preissued', fatal=False)
            except Exception as error:
                self.write_debug(
                    f'VideoFetch Niconico: pre-issued v4 HLS unavailable; '
                    f'trying access rights ({error})')

        route = 'v4-preissued-dms'
        if not formats:
            formats = self._request_dms_formats(
                watch_data, video_id, guest=guest, action_track_id=watch_track_id)
            route = 'v4-access-rights-dms'
        if not formats:
            reason = watch_data.get('okReason') or 'no playable media'
            raise ExtractorError(
                f'Niconico v4 returned no usable DMS/HLS formats (okReason={reason})',
                expected=True)

        self.write_debug(
            f'VideoFetch Niconico: v4 returned {len(formats)} DMS HLS formats '
            f'({"guest" if guest else "signed-in"} session)')
        return self._make_metadata(watch_data, video_id, route, formats)

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
        headers = dict(self._FRONTEND_HEADERS)
        if guest:
            headers['Cookie'] = ''
        response = self._download_json(
            endpoint, video_id,
            note=f'Requesting Niconico {path} watch API',
            headers=headers,
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
        client = watch_data.get('client') or {}
        watch_track_id = client.get('watchTrackId') or action_track_id
        formats = self._request_dms_formats(
            watch_data, video_id, guest=guest, action_track_id=watch_track_id)
        if not formats:
            raise ExtractorError(
                f'Niconico {path} API returned no available DMS formats',
                expected=True)
        route = 'v3_guest-dms' if guest else 'v3-dms'
        self.write_debug(
            f'VideoFetch Niconico: {path} API returned {len(formats)} DMS HLS formats')
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
                    return data, 'watchV4'
            return response, 'server-response'
        return None, ''

    def _extract_watch_page_dms(self, url, video_id):
        watch_data, route = self._get_watch_response(url, video_id)
        if not isinstance(watch_data, dict):
            return None
        guest = watch_data.get('viewer') is None
        client = watch_data.get('client') or {}
        formats = self._request_dms_formats(
            watch_data, video_id, guest=guest,
            action_track_id=client.get('watchTrackId'))
        if not formats:
            return None
        api_route = 'watchV4-access-rights-dms' if route == 'watchV4' else 'server-response-dms'
        self.write_debug(f'VideoFetch Niconico: {route} returned {len(formats)} DMS HLS formats')
        return self._make_metadata(watch_data, video_id, api_route, formats)

    def _real_extract(self, url):
        video_id = self._match_id(url)

        # Current player path: NVAPI v4 + pre-issued DMS/HLS. A v4 rejection
        # falls through to legacy v3 and finally the upstream yt-dlp extractor.
        try:
            result = self._extract_v4_api(video_id)
            if result:
                return result
        except Exception as error:
            self.write_debug(
                f'VideoFetch Niconico: v4 route unavailable; trying legacy v3 ({error})')

        action_track_id = self._new_action_track_id()
        guest_order = (False, True) if super().is_logged_in else (True,)
        for guest in guest_order:
            try:
                return self._extract_v3_api(video_id, guest, action_track_id)
            except Exception as error:
                route = 'v3_guest' if guest else 'v3'
                self.write_debug(
                    f'VideoFetch Niconico: {route} DMS route unavailable '
                    f'({"no further v3 fallback" if guest else "trying v3_guest"}): {error}')

        try:
            result = self._extract_watch_page_dms(url, video_id)
            if result:
                return result
        except Exception as error:
            self.write_debug(
                f'VideoFetch Niconico: watch-page DMS route unavailable ({error})')

        # Final compatibility fallback for older or non-DMS videos.
        result = super()._real_extract(url)
        result['videofetch_niconico_api_mode'] = (
            'v3' if super().is_logged_in else 'v3_guest')
        return result
