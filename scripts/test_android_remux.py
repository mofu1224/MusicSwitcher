"""Check that the Android app can extract AAC from MP4 without re-encoding."""

import argparse
from pathlib import Path
import re
from shlex import quote
import subprocess
import tempfile
import uuid

from smoke_android import Android, PACKAGE

VIDEO_URL = "https://samplelib.com/lib/preview/mp4/sample-5s.mp4"
AUDIO_SHA256 = "aa554cb46f31b100a969d0d8b4536a9a0eb2911609cf88a9ae5b5c77e5876dd9"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--ffmpeg", default="ffmpeg", help="Host FFmpeg for verifying the saved stream")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--avd", required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--page-size", required=True, choices=("4096", "16384"))
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial):
        parser.error("Use a disposable emulator, not a personal device.")
    device = Android(args.adb, args.serial)
    device.require_avd(args.avd)
    assert device.sh("getconf", "PAGE_SIZE") == args.page_size

    folder = f"MusicSwitcher-Remux-{uuid.uuid4().hex[:5]}"
    root = f"/sdcard/Music/{folder}"
    device.sh("mkdir", "-p", root)
    print(device.adb("install", "-r", str(args.apk.resolve())), flush=True)
    device.sh("am", "force-stop", PACKAGE)
    device.start(clear_task=True)
    device.choose_music_folder(folder)

    device.download(VIDEO_URL)
    status = device.node("Saved: ", "Fetch failed.", "Save failed.", prefix=True, timeout=120).get("text", "")
    assert status.startswith("Saved: "), status
    title = status.removeprefix("Saved: ")
    assert title.endswith(".m4a"), title
    assert device.sh("ls", "-1", quote(root)).splitlines() == [title]

    with tempfile.TemporaryDirectory(prefix="musicswitcher-remux-") as temp:
        output = Path(temp) / "saved.m4a"
        device.adb("pull", f"{root}/{title}", str(output))
        streams = subprocess.check_output(
            [args.ffmpeg, "-hide_banner", "-loglevel", "error", "-i", str(output),
             "-map", "0:a:0", "-c", "copy", "-f", "hash", "-hash", "sha256", "-"],
            encoding="utf-8", timeout=30,
        ).strip()
        assert streams == f"SHA256={AUDIO_SHA256}", streams

    device.tap(title)
    device.wait(lambda: device.playing(title), "play the extracted M4A")
    print(f"PASS: {args.page_size}-byte pages; MP4 became a playable M4A with unchanged AAC data", flush=True)


if __name__ == "__main__":
    main()
