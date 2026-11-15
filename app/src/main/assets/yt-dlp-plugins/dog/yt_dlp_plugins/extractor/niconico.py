"""Dog's Niconico API strategy.

Try the normal v3 watch API first, even for public videos without a session.
If that endpoint rejects an anonymous request, retry with yt-dlp's standard
v3_guest path. All HLS parsing, stream selection and merging remain in yt-dlp.
"""

from yt_dlp.extractor.niconico import NiconicoIE as _NiconicoIE
from yt_dlp.networking.exceptions import HTTPError
from yt_dlp.utils import ExtractorError


class DogNiconicoIE(_NiconicoIE, plugin_name='dog_niconico_v3_first'):
    @property
    def is_logged_in(self):
        # The built-in extractor uses this property only to select v3/v3_guest
        # here. Preserve real session detection, but allow one v3-first attempt.
        return (
            getattr(self, '_dog_try_standard_v3', False)
            or super().is_logged_in
        )

    def _real_extract(self, url):
        # A real user_session already selects v3 in the upstream extractor.
        if super().is_logged_in:
            return super()._real_extract(url)

        self._dog_try_standard_v3 = True
        try:
            try:
                result = super()._real_extract(url)
                self.write_debug(
                    'Dog Niconico plugin: standard v3 API succeeded without a session')
                return result
            except (ExtractorError, HTTPError) as error:
                # Do not fake a cookie or bypass access checks. Retry the
                # upstream guest endpoint and let Nico decide availability.
                self._dog_try_standard_v3 = False
                self.write_debug(
                    f'Dog Niconico plugin: standard v3 API was unavailable; '
                    f'retrying v3_guest ({error})')
                return super()._real_extract(url)
        finally:
            self._dog_try_standard_v3 = False
