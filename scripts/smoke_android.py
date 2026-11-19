"""Run the MusicSwitcher smoke test on a disposable English API 36 emulator."""

import argparse
import hashlib
import math
from pathlib import Path
import re
import struct
import subprocess
import tempfile
import time
import uuid
import wave
import xml.etree.ElementTree as ET
import zipfile


PACKAGE = "dev.musicswitcher"
SAMPLE_URL = "https://samplelib.com/lib/preview/mp3/sample-3s.mp3"
YTDLP_SHA256 = "e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3"


class Android:
    def __init__(self, adb, serial):
        self.base = [adb, "-s", serial]

    def adb(self, *args):
        return subprocess.check_output(
            self.base + list(args), encoding="utf-8", errors="replace", timeout=120,
        ).strip()

    def sh(self, *args):
        return self.adb("shell", *args)

    def wait(self, check, description, timeout=30):
        end = time.monotonic() + timeout
        last_error = None
        while time.monotonic() < end:
            try:
                result = check()
                if result is not None and result is not False:
                    return result
            except (ET.ParseError, subprocess.CalledProcessError) as error:
                last_error = error
            time.sleep(0.4)
        raise AssertionError(f"Timed out: {description}; last error: {last_error}")

    def ui(self):
        path = "/data/local/tmp/musicswitcher-ui.xml"
        result = self.sh("uiautomator", "dump", path)
        if "dumped to:" not in result:
            raise ET.ParseError(result)
        return ET.fromstring(self.sh("cat", path))

    def node(self, *labels, prefix=False, timeout=25):
        def find():
            for node in self.ui().iter("node"):
                for attribute in ("text", "content-desc"):
                    value = node.get(attribute, "")
                    if any(value.startswith(label) if prefix else value == label for label in labels):
                        return node
            return None
        return self.wait(find, f"UI: {labels}", timeout)

    def tap(self, *labels, prefix=False):
        self.tap_node(self.node(*labels, prefix=prefix))

    def tap_node(self, node, fraction=0.5):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
        self.sh("input", "tap", str(round(x1 + (x2 - x1) * fraction)), str((y1 + y2) // 2))

    def start(self, clear_task=False):
        flags = ("-f", "0x10008000") if clear_task else ()
        self.sh("am", "start", "-W", *flags, "-n", f"{PACKAGE}/.MainActivity")

    def require_avd(self, expected):
        if self.adb("emu", "avd", "name").splitlines()[0] != expected:
            raise ValueError(f"Expected dedicated AVD {expected!r} before modifying the device")

    def open_folder_picker(self):
        control = self.node("Choose folder", "Libraries")
        self.tap_node(control)
        if control.get("content-desc") == "Libraries":
            self.tap("Add library")

    def choose_music_folder(self, folder):
        self.open_folder_picker()
        self.tap("Music")
        self.tap(folder)
        self.tap("USE THIS FOLDER", "Use this folder")
        self.tap("Allow", "ALLOW")

    def cancel_folder_picker(self):
        self.node("USE THIS FOLDER", "Use this folder")
        for _ in range(6):
            if any(node.get("package") == PACKAGE for node in self.ui().iter("node")):
                return
            self.sh("input", "keyevent", "KEYCODE_BACK")
        raise AssertionError("The folder picker did not return to MusicSwitcher")

    def media(self):
        return self.sh("dumpsys", "media_session")

    def download(self, url):
        self.tap("Import link")
        self.tap("URL")
        self.sh("input", "text", url)
        self.tap("Save")

    def playing(self, title):
        return self.playback_position(title, 3) is not None

    def playback_position(self, title, state_number):
        for state in self.music_sessions():
            if f"description={title}," not in state:
                continue
            match = re.search(rf"PlaybackState \{{state=(?:{state_number}|\w+\({state_number}\)), position=(\d+)", state)
            if match:
                return int(match.group(1))
        return None

    def music_sessions(self):
        lines = self.media().splitlines()
        for index, line in enumerate(lines):
            if line.strip() != f"package={PACKAGE}":
                continue
            indent = len(line) - len(line.lstrip())
            details = []
            for detail in lines[index + 1:]:
                if len(detail) - len(detail.lstrip()) < indent or detail.lstrip().startswith("package="):
                    break
                details.append(detail)
            yield "\n".join(details)

    def screenshot(self, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(subprocess.check_output(self.base + ["exec-out", "screencap", "-p"], timeout=30))


def write_tone(path, seconds=25):
    with wave.open(str(path), "wb") as wav:
        rate = 16_000
        wav.setparams((1, 2, rate, 0, "NONE", "not compressed"))
        frames = [int(1_400 * math.sin(math.tau * 440 * n / rate)) for n in range(rate)]
        wav.writeframes(struct.pack(f"<{rate}h", *frames) * seconds)


def check_player_ui(device, local_title, next_title, screenshots):
    if screenshots:
        device.screenshot(screenshots / "library-dark.png")
    device.tap("Open player")
    device.node("Back to library")
    device.node("Seek")
    if screenshots:
        device.screenshot(screenshots / "now-playing-dark.png")
    device.tap("Pause")
    device.wait(lambda: device.playback_position(local_title, 2) is not None, "pause in full player")
    device.tap_node(device.node("Seek"), 0.33)
    device.wait(lambda: 25_000 <= (device.playback_position(local_title, 2) or 0) <= 49_000, "seek in full player")
    device.tap("Next")
    device.wait(lambda: device.playback_position(next_title, 2) is not None, "next track in full player")
    device.tap("Previous")
    device.wait(lambda: device.playback_position(local_title, 2) is not None, "previous track in full player")
    device.tap("Play")
    device.wait(lambda: device.playing(local_title), "resume in full player")
    device.tap("Back to library")
    device.node("Libraries")
    device.tap("Open player")
    device.node("Back to library")
    device.sh("input", "keyevent", "KEYCODE_BACK")
    device.node("Libraries")
    assert device.playing(local_title)
    print("PASS: mini-player, full player, pause/resume, seek, previous/next and back navigation", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--avd", help="Verify the expected disposable AVD name before testing")
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--edge-cases", action="store_true")
    parser.add_argument("--release", action="store_true", help="Verify packaged core without debug-only run-as access")
    parser.add_argument("--player-ui", action="store_true")
    parser.add_argument("--screenshots", type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial):
        parser.error("Use a disposable emulator, not a personal device.")
    device = Android(args.adb, args.serial)
    if args.avd:
        device.require_avd(args.avd)
    folder = f"MusicSwitcher-QA-{uuid.uuid4().hex[:5]}"
    root = f"/sdcard/Music/{folder}"
    local_title = "MusicSwitcher-tone.wav"
    next_title = "MusicSwitcher-z-next.wav"

    with tempfile.TemporaryDirectory(prefix="musicswitcher-") as work:
        fixture = Path(work) / local_title
        write_tone(fixture, 120 if args.player_ui else 25)
        device.sh("mkdir", "-p", f"{root}/sub")
        device.adb("push", str(fixture), f"{root}/sub/{local_title}")
        if args.player_ui:
            device.adb("push", str(fixture), f"{root}/sub/{next_title}")
        print(device.adb("install", "-r", str(args.apk.resolve())), flush=True)
        device.sh("am", "force-stop", PACKAGE)
        device.start(clear_task=True)
        device.choose_music_folder(folder)
        device.node(local_title)
        print("PASS: SAF picker and recursive local audio listing", flush=True)

        if args.player_ui:
            device.node("Libraries")
            device.tap("Light mode")
            device.node("Dark mode")
            if args.screenshots:
                device.screenshot(args.screenshots / "library-light.png")
        device.sh("am", "force-stop", PACKAGE)
        device.start()
        device.node(local_title)
        print("PASS: persisted folder permission after process restart", flush=True)
        if args.player_ui:
            device.tap("Dark mode")
            device.node("Light mode")
            print("PASS: light and dark appearance persists across restart", flush=True)
        device.tap(local_title)
        device.wait(lambda: device.playing(local_title), "local audio playing")
        if args.player_ui:
            check_player_ui(device, local_title, next_title, args.screenshots)
        device.sh("input", "keyevent", "KEYCODE_HOME")
        device.wait(lambda: device.playing(local_title), "background playback")
        assert "isForeground=true" in device.sh("dumpsys", "activity", "services", PACKAGE)
        print("PASS: local audio and foreground media playback after HOME", flush=True)
        device.start()

        device.download(SAMPLE_URL)
        message = device.node("Saved: ", "Fetch failed.", "Save failed.", prefix=True, timeout=120)
        status = message.get("text", "")
        assert status.startswith("Saved: "), status
        title = status.removeprefix("Saved: ")
        assert title.endswith(".mp3"), title
        persisted = device.sh("ls", "-1", root).splitlines()
        assert title in persisted and not any(item.endswith(".partial") for item in persisted), persisted
        output = Path(work) / "saved.mp3"
        device.adb("pull", f"{root}/{title}", str(output))
        data = output.read_bytes()
        assert hashlib.sha256(data).hexdigest() == "0244590f2b4bcb62352b574e78bea940e8d89cfa69823b5208ef4c43e0abcb44"
        if args.release:
            core_size = (Path(__file__).resolve().parents[1] / "app/src/main/res/raw/ytdlp").stat().st_size
            with zipfile.ZipFile(args.apk) as apk:
                # aapt2 shortens resource filenames in release APKs.
                assert any(hashlib.sha256(apk.read(entry)).hexdigest() == YTDLP_SHA256
                           for entry in apk.infolist() if entry.file_size == core_size)
        else:
            installed_hash = device.sh("run-as", PACKAGE, "sha256sum", "no_backup/youtubedl-android/yt-dlp/yt-dlp")
            assert installed_hash.split()[0] == YTDLP_SHA256, installed_hash
        print(f"PASS: embedded yt-dlp + FFmpeg saved {len(data):,} bytes; sha256={hashlib.sha256(data).hexdigest()}", flush=True)

        device.sh("cmd", "connectivity", "airplane-mode", "enable")
        device.sh("svc", "wifi", "disable")
        try:
            assert device.sh("settings", "get", "global", "airplane_mode_on") == "1"
            device.tap(title)
            device.wait(lambda: device.playing(title), "saved audio playing offline", timeout=10)
            print("PASS: downloaded song played with airplane mode on and Wi-Fi off", flush=True)
            if args.edge_cases:
                device.download(SAMPLE_URL)
                device.node("Fetch failed.", prefix=True, timeout=70)
                assert device.sh("ls", "-1", root).splitlines() == persisted
                print("PASS: offline import failure left existing files intact", flush=True)
        finally:
            device.sh("cmd", "connectivity", "airplane-mode", "disable")
            device.sh("svc", "wifi", "enable")

        if args.edge_cases:
            device.wait(lambda: 'Wifi is connected to' in device.sh("cmd", "wifi", "status"), "Wi-Fi reconnect")
            device.download(f"https://httpbingo.org/delay/10?probe={uuid.uuid4().hex}")
            device.tap("Cancel")
            device.node("Canceled")
            if not args.release:
                device.wait(lambda: "audio-" not in device.sh("run-as", PACKAGE, "ls", "cache"), "temporary download cleanup")
            assert device.sh("ls", "-1", root).splitlines() == persisted
            print("PASS: cancelled import left no incomplete files", flush=True)

            device.sh("mv", root, f"{root}-moved")
            try:
                device.tap("Refresh")
                device.node("Folder unavailable.", prefix=True)
                print("PASS: moved folder reported a re-selection error", flush=True)
            finally:
                device.sh("mv", f"{root}-moved", root)


if __name__ == "__main__":
    main()
