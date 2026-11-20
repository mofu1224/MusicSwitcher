"""Exercise shuffle and three overlapping imports on a disposable Android emulator."""

import argparse
from pathlib import Path
import re
import tempfile
import uuid

from smoke_android import Android, PACKAGE, write_tone


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--avd", required=True)
    parser.add_argument("--adb", default="adb")
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial):
        parser.error("Use a disposable emulator.")
    device = Android(args.adb, args.serial)
    device.require_avd(args.avd)
    folder = f"MusicSwitcher-Queue-{uuid.uuid4().hex[:5]}"
    root = f"/sdcard/Music/{folder}"
    device.sh("mkdir", "-p", root)
    tracks = {f"track-{i}.wav" for i in range(3)}
    with tempfile.TemporaryDirectory() as work:
        fixture = Path(work) / "tone.wav"
        write_tone(fixture, 60)
        for track in sorted(tracks):
            device.adb("push", str(fixture), f"{root}/{track}")
    device.sh("am", "force-stop", PACKAGE)
    device.start(clear_task=True)
    device.choose_music_folder(folder)
    device.tap("Shuffle")
    remaining = tracks.copy()
    while remaining:
        song = device.wait(lambda: next((t for t in remaining if device.playing(t)), None),
                           "next distinct library track")
        remaining.remove(song)
        if remaining:
            device.sh("input", "keyevent", "KEYCODE_MEDIA_NEXT")
    print("PASS: shuffle played each library track once", flush=True)

    wifi_on = device.sh("settings", "get", "global", "wifi_on") == "1"
    try:
        # The emulator speed limit applies to cellular traffic, not Wi-Fi.
        device.sh("svc", "wifi", "disable")
        device.adb("emu", "network", "speed", "gsm")
        for _ in range(3):
            device.download("https://samplelib.com/lib/preview/mp3/sample-15s.mp3")
        device.node("Queued #3")
        labels = [n.get("text", "") for n in device.ui().iter("node")]
        assert sum(label.startswith("Fetching") for label in labels) == 2, labels
        print("PASS: two fetching, third queued; input remained available", flush=True)
        device.tap("Cancel")
        device.node("Canceled", timeout=45)
        device.node("Close")
        device.tap("Close")
        device.wait(lambda: any(n.get("text", "").startswith("Fetching") and
                                n.get("text", "").endswith("#3") for n in device.ui().iter("node")),
                    "third import started after cancel")
    finally:
        device.adb("emu", "network", "speed", "full")
        if wifi_on:
            device.sh("svc", "wifi", "enable")
    def saved_files():
        files = device.sh("ls", "-1", root).splitlines()
        return len([f for f in files if f.endswith(".mp3")]) == 2 and not any(f.endswith(".partial") for f in files)
    device.wait(saved_files, "both remaining imports saved without collision", timeout=180)
    print("PASS: cancellation freed a slot; both other copies saved", flush=True)


if __name__ == "__main__":
    main()
