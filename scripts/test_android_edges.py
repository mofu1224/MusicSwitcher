"""Exercise storage, malformed audio and recovery on a named disposable Android AVD."""

import argparse
from pathlib import Path
from shlex import quote
import tempfile
import re
import uuid

from smoke_android import Android, PACKAGE, SAMPLE_URL, write_tone


def drag_after(device, first, second):
    def center(label):
        left, top, right, bottom = map(int, re.findall(r"\d+", device.node(label).get("bounds")))
        return (left + right) // 2, (top + bottom) // 2
    _, y = center(first)
    _, ty = center(second)
    assert y < ty, (first, second)
    grips = [list(map(int, re.findall(r"\d+", n.get("bounds"))))
             for n in device.ui().iter("node") if n.get("text") == "≡"]
    left, top, right, bottom = min(grips, key=lambda b: abs((b[1] + b[3]) // 2 - y))
    x = str((left + right) // 2)
    device.sh("input", "swipe", x, str((top + bottom) // 2), x, str(ty), "400")
    assert center(first)[1] > center(second)[1], "drop did not change order"


def saved_name(device):
    message = device.node(
        "Saved: ", "Fetch failed.", "Save failed.",
        prefix=True, timeout=110,
    ).get("text", "")
    assert message.startswith("Saved: "), message
    return message.removeprefix("Saved: ")


def track_hash(device, path):
    return device.sh("sha256sum", quote(path)).split()[0]


def check_download_name_collision(device, root):
    device.download(SAMPLE_URL)
    first = saved_name(device)
    assert first.endswith(".mp3"), first
    digest = track_hash(device, f"{root}/{first}")
    device.tap("Close")
    device.download(SAMPLE_URL)
    second = saved_name(device)
    assert second == f"{Path(first).stem} (2).mp3", (first, second)
    assert track_hash(device, f"{root}/{first}") == digest
    assert track_hash(device, f"{root}/{second}") == digest
    files = device.sh("ls", "-1", root).splitlines()
    assert first in files and second in files
    assert not any(item.endswith(".partial") for item in files), files
    device.tap("Close")
    print("PASS: repeated download created a suffixed copy; original SHA-256 unchanged", flush=True)


def check_refresh_after_folder_returns(device, root, title):
    device.sh("mv", root, f"{root}-moved")
    try:
        device.tap("Refresh")
        device.node("Folder unavailable.", prefix=True)
    finally:
        device.sh("mv", f"{root}-moved", root)
    device.tap("Refresh")
    device.node(Path(root).name, timeout=12)
    device.node(title, timeout=12)
    print("PASS: a restored folder can be refreshed without reselecting it", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--avd", required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--stress-tracks", type=int, default=512, choices=(0, 128, 512, 1024))
    args = parser.parse_args()
    device = Android(args.adb, args.serial)
    device.require_avd(args.avd)
    test_id = uuid.uuid4().hex[:6]
    folder = f"MusicSwitcher-Edge-{test_id}"
    empty = f"MusicSwitcher-Empty-{test_id}"
    root = f"/sdcard/Music/{folder}"
    empty_root = f"/sdcard/Music/{empty}"
    corrupt = "00-invalid.mp3"
    valid = "日本語の曲.wav"

    with tempfile.TemporaryDirectory(prefix="musicswitcher-edge-") as work:
        fixtures = Path(work)
        write_tone(fixtures / valid, 120)
        (fixtures / corrupt).write_bytes(b"This audio file is intentionally invalid.")
        (fixtures / "notes.txt").write_text("Not an audio track.", encoding="utf-8")
        (fixtures / "ignored.m4a.part").write_bytes(b"Incomplete download fixture.")
        device.sh("mkdir", "-p", f"{root}/sub", empty_root)
        for name in (corrupt, "ignored.m4a.part"):
            device.adb("push", str(fixtures / name), f"{root}/{name}")
        device.adb("push", str(fixtures / valid), f"{root}/sub/{valid}")
        device.adb("push", str(fixtures / "notes.txt"), f"{empty_root}/notes.txt")
        print(device.adb("install", "-r", str(args.apk.resolve())), flush=True)
        device.sh("am", "force-stop", PACKAGE)
        device.start(clear_task=True)
        device.choose_music_folder(folder)
        device.node("2 tracks")
        drag_after(device, corrupt, valid)
        device.tap("Refresh")
        visible = [n.get("text") for n in device.ui().iter("node")]
        assert visible.index(valid) < visible.index(corrupt)
        device.tap(corrupt)
        device.node("Cannot play track")
        device.tap(valid)
        device.wait(lambda: device.playing(valid), "valid audio after playback error")
        print("PASS: incomplete file excluded, corrupt audio reported, Unicode WAV played afterward", flush=True)

        device.open_folder_picker()
        device.cancel_folder_picker()
        device.node(valid)
        assert device.playing(valid), "Cancelling folder picker interrupted playback"
        print("PASS: cancelling the folder picker keeps selection and playback", flush=True)

        device.tap("Import link")
        device.tap("URL")
        device.sh("input", "text", "not-a-link")
        device.node("not-a-link")
        device.sh("input", "keyevent", "KEYCODE_ENTER")
        device.node("Use an HTTPS link.")
        device.tap("Close")
        assert not any(item.endswith(".m4a") for item in device.sh("ls", "-1", root).splitlines())
        print("PASS: invalid URL rejected in the dialog without writing files", flush=True)

        check_download_name_collision(device, root)
        check_refresh_after_folder_returns(device, root, valid)
        device.tap(valid)
        device.wait(lambda: device.playing(valid), "playing on original selection")
        device.choose_music_folder(empty)
        device.node("No tracks", prefix=True)
        device.wait(lambda: not device.playing(valid), "old queue stopped after choosing a different folder")
        device.sh("am", "force-stop", PACKAGE)
        device.start()
        device.node(empty)
        device.node("0 tracks")
        print("PASS: changing folders stopped the old queue and persisted the new empty selection", flush=True)

        device.tap("Libraries")
        drag_after(device, folder, empty)
        device.tap(folder)
        device.node(valid)
        device.sh("am", "force-stop", PACKAGE)
        device.start()
        device.node(valid)
        device.tap("Libraries")
        device.tap(empty)
        device.node("No tracks")
        print("PASS: both libraries retain separate contents and grants across restart", flush=True)

        if args.stress_tracks:
            short_track = fixtures / "stress.wav"
            write_tone(short_track, 3)
            device.adb("push", str(short_track), f"{root}/stress.wav")
            destination = f"{empty_root}/level1/level2"
            device.sh("mkdir", "-p", destination)
            count = args.stress_tracks
            command = (
                f"for i in $(seq 1 {count}); do "
                f"cp {root}/stress.wav {destination}/track-$(printf '%04d' \"$i\").wav || exit 1; done"
            )
            device.sh(command)
            device.tap("Refresh")
            device.node(f"{count} tracks", timeout=45)
            device.tap("track-0001.wav")
            device.wait(lambda: device.playing("track-0001.wav"), "playback from a large queue")
            print(f"PASS: scanned a nested {count}-track folder and started its playback queue", flush=True)
            device.download(SAMPLE_URL)
            saved = saved_name(device)
            bounds = device.node("track-0001.wav").get("bounds")
            left, top, right, bottom = map(int, re.findall(r"\d+", bounds))
            x = str((left + right) // 2)
            device.sh("input", "swipe", x, str(bottom + 580), x, str(top), "250")
            visible = [n.get("text") for n in device.ui().iter("node")]
            assert "track-0001.wav" not in visible and f"Saved: {saved}" in visible
            device.tap("Close")
            print("PASS: import result remains visible and dismissible after scrolling", flush=True)


if __name__ == "__main__":
    main()
