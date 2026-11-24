"""Check the Manual / A-Z track order presets on a disposable Android emulator."""

import argparse
from pathlib import Path
import re
import tempfile
import uuid

from smoke_android import Android, PACKAGE, write_tone
from test_android_edges import drag_after

NAME_ORDER = "A–Z"


def visible_order(device, names):
    nodes = [n for n in device.ui().iter("node") if n.get("text") in names]
    return [n.get("text") for n in sorted(nodes, key=lambda n: int(re.findall(r"\d+", n.get("bounds"))[1]))]


def has_grips(device):
    return any(n.get("text") == "≡" for n in device.ui().iter("node"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--avd", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--apk", type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial):
        parser.error("Use a disposable emulator.")
    device = Android(args.adb, args.serial)
    device.require_avd(args.avd)
    if args.apk:
        device.adb("install", "-r", str(args.apk.resolve()))
    folder = f"MusicSwitcher-Sort-{uuid.uuid4().hex[:5]}"
    root = f"/sdcard/Music/{folder}"
    names = ["b.wav", "a.wav", "c.wav"]
    device.sh("mkdir", "-p", root)
    with tempfile.TemporaryDirectory() as work:
        fixture = Path(work) / "tone.wav"
        write_tone(fixture, 5)
        for name in names:
            device.adb("push", str(fixture), f"{root}/{name}")
    device.sh("am", "force-stop", PACKAGE)
    device.start(clear_task=True)
    device.choose_music_folder(folder)
    device.node("Manual")
    device.wait(lambda: visible_order(device, names) == ["a.wav", "b.wav", "c.wav"], "initial scan order")
    drag_after(device, "a.wav", "c.wav")
    manual = visible_order(device, names)
    assert manual == ["b.wav", "c.wav", "a.wav"], manual
    print(f"PASS: manual drag order {manual}", flush=True)

    device.tap(NAME_ORDER)
    device.wait(lambda: visible_order(device, names) == ["a.wav", "b.wav", "c.wav"], "A-Z order")
    assert not has_grips(device), "drag handles must be hidden in A-Z"
    print("PASS: A-Z order without drag handles", flush=True)

    device.sh("am", "force-stop", PACKAGE)
    device.start(clear_task=True)
    device.wait(lambda: visible_order(device, names) == ["a.wav", "b.wav", "c.wav"], "A-Z kept after restart")
    print("PASS: A-Z preset kept across restart", flush=True)

    device.tap("Manual")
    device.wait(lambda: visible_order(device, names) == manual, "manual order restored")
    assert has_grips(device)
    device.sh("am", "force-stop", PACKAGE)
    device.start(clear_task=True)
    device.wait(lambda: visible_order(device, names) == manual, "manual order kept after restart")
    print("PASS: switching back restored the previous manual order, also after restart", flush=True)


if __name__ == "__main__":
    main()
