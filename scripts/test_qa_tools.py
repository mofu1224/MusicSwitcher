"""Host-side tests for the Android and APK QA helpers."""

from io import BytesIO
from struct import pack_into
import unittest
from unittest.mock import patch
from zipfile import ZipFile

from check_native_alignment import load_segments, native_files
from smoke_android import Android


def sample_elf(alignment):
    data = bytearray(120)
    data[:6] = b"\x7fELF\x02\x01"
    pack_into("<Q", data, 32, 64)
    pack_into("<HH", data, 54, 56, 1)
    pack_into("<IIQQQQQQ", data, 64, 1, 5, 0, 0, 0, 120, 120, alignment)
    return bytes(data)


class MediaSessionTest(unittest.TestCase):
    def test_song_and_play_state_must_be_from_music_switcher(self):
        dump = """Sessions Stack:
      other session
        package=com.example.other
        state=PlaybackState {state=PLAYING(3), position=200,
        metadata: description=local.wav, null
      MusicSwitcher session
        package=dev.musicswitcher
        state=PlaybackState {state=PAUSED(2), position=350,
        metadata: description=local.wav, null
"""
        device = Android("adb", "emulator-5556")
        with patch.object(device, "media", return_value=dump):
            self.assertFalse(device.playing("local.wav"))
            self.assertEqual(350, device.playback_position("local.wav", 2))

    def test_missing_session_never_claims_playback(self):
        device = Android("adb", "emulator-5556")
        with patch.object(device, "media", return_value="Sessions Stack: empty"):
            self.assertIsNone(device.playback_position("local.wav", 3))


class NativeAlignmentTest(unittest.TestCase):
    def test_reads_both_page_size_alignments(self):
        for alignment in (4_096, 16_384):
            with self.subTest(alignment=alignment):
                self.assertEqual([(0, 0, alignment)], load_segments(sample_elf(alignment)))

    def test_finds_versioned_shared_objects_inside_runtime_zip(self):
        nested = BytesIO()
        with ZipFile(nested, "w") as archive:
            archive.writestr("usr/lib/libcodec.so", "libcodec.so.1")  # Symlink representation.
            archive.writestr("usr/lib/libcodec.so.1", sample_elf(4_096))
        apk = BytesIO()
        with ZipFile(apk, "w") as archive:
            archive.writestr("lib/x86_64/libffmpeg.so", sample_elf(16_384))
            archive.writestr("lib/x86_64/libffmpeg.zip.so", nested.getvalue())
        with ZipFile(apk) as archive:
            found = list(native_files(archive))
        self.assertEqual(2, len(found))
        self.assertEqual(("x86_64", "lib/x86_64/libffmpeg.zip.so!/usr/lib/libcodec.so.1", [(0, 0, 4_096)]), found[1])


if __name__ == "__main__":
    unittest.main()
