"""Verify file deletion and library removal using only this run's generated fixtures."""

import argparse
from pathlib import Path
import re
import tempfile
import uuid

from smoke_android import Android, PACKAGE, write_tone
from test_android_edges import drag_after


def check_gestures(device, label, action):
    left, top, right, bottom = map(int, re.findall(r"\d+", device.node(label).get("bounds")))
    x, y = str((left + right) // 2), str((top + bottom) // 2)
    device.sh("input", "swipe", x, y, str(max(5, int(x) - 180)), y, "160")
    assert not any(n.get("text") == action for n in device.ui().iter("node"))
    device.sh("input", "motionevent", "DOWN", x, y)
    try:
        device.node(action, timeout=6)  # Must be visible before ACTION_UP.
    finally:
        device.sh("input", "motionevent", "UP", x, y)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--avd", required=True)
    parser.add_argument("--apk", type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial):
        parser.error("A disposable emulator is required")
    device = Android(args.adb, args.serial)
    device.require_avd(args.avd)
    folder = f"Removal-{uuid.uuid4().hex[:8]}"
    root = f"/sdcard/Music/{folder}"
    with tempfile.TemporaryDirectory() as work:
        sample = Path(work) / "tone.wav"
        write_tone(sample, 30)
        device.sh("mkdir", "-p", root)
        for name in ("remove.wav", "keep.wav"):
            device.adb("push", str(sample), f"{root}/{name}")
    device.adb("install", "-r", str(args.apk.resolve()))
    device.start()
    device.choose_music_folder(folder)
    drag_after(device, "keep.wav", "remove.wav")
    device.tap("keep.wav")
    device.wait(lambda: device.playing("keep.wav"), "local playback")
    check_gestures(device, "remove.wav", "Delete file")
    device.tap("Cancel")
    check_gestures(device, "remove.wav", "Delete file")
    device.tap("Delete file")
    device.node("1 track")
    assert device.sh("ls", "-1", root) == "keep.wav"
    device.tap("Libraries")
    check_gestures(device, folder, "Remove library")
    device.tap("Remove library")
    device.wait(lambda: not any(n.get("text") in (folder, "Remove library")
                                for n in device.ui().iter("node")), "library removal applied")
    device.sh("am", "force-stop", PACKAGE)
    device.start()
    control = device.node("Choose folder", "Libraries")
    if control.get("content-desc") == "Libraries":
        device.tap_node(control)
        assert not any(n.get("text") == folder for n in device.ui().iter("node"))
    assert device.sh("ls", "-1", root) == "keep.wav"
    print("PASS: playback, cancelled/confirmed deletion, removal across restart without deleting folder contents")


if __name__ == "__main__":
    main()
