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


PACKAGE = "dev.musicswitcher"
SAMPLE_URL = "https://samplelib.com/lib/preview/mp3/sample-3s.mp3"
YTDLP_SHA256 = "e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3"


class Android:
    def __init__(self, adb, serial):
        self.base = [adb, "-s", serial]

    def adb(self, *args):
        return subprocess.check_output(self.base + list(args), encoding="utf-8", errors="replace").strip()

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

    def media(self):
        return self.sh("dumpsys", "media_session")

    def download(self, url):
        self.tap("URLから保存")
        self.tap("音楽のURL")
        self.sh("input", "text", url)
        self.tap("保存する")

    def playing(self, title):
        return self.playback_position(title, 3) is not None

    def playback_position(self, title, state_number):
        state = self.media()
        if f"description={title}," not in state:
            return None
        match = re.search(rf"PlaybackState \{{state=(?:{state_number}|\w+\({state_number}\)), position=(\d+)", state)
        return int(match.group(1)) if match else None

    def screenshot(self, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(subprocess.check_output(self.base + ["exec-out", "screencap", "-p"]))


def write_tone(path, seconds=25):
    with wave.open(str(path), "wb") as wav:
        rate = 16_000
        wav.setparams((1, 2, rate, 0, "NONE", "not compressed"))
        frames = [int(1_400 * math.sin(math.tau * 440 * n / rate)) for n in range(rate)]
        wav.writeframes(struct.pack(f"<{rate}h", *frames) * seconds)


def check_player_ui(device, local_title, next_title, screenshots):
    if screenshots:
        device.screenshot(screenshots / "library-dark.png")
    device.tap("再生画面を開く")
    device.node("再生中")
    device.node("再生位置")
    if screenshots:
        device.screenshot(screenshots / "now-playing-dark.png")
    device.tap("一時停止")
    device.wait(lambda: device.playback_position(local_title, 2) is not None, "pause in full player")
    device.tap_node(device.node("再生位置"), 0.33)
    device.wait(lambda: 25_000 <= (device.playback_position(local_title, 2) or 0) <= 49_000, "seek in full player")
    device.tap("次へ")
    device.wait(lambda: device.playback_position(next_title, 2) is not None, "next track in full player")
    device.tap("前へ")
    device.wait(lambda: device.playback_position(local_title, 2) is not None, "previous track in full player")
    device.tap("再生")
    device.wait(lambda: device.playing(local_title), "resume in full player")
    device.tap("ライブラリへ戻る")
    device.node("ライブラリ")
    device.tap("再生画面を開く")
    device.node("再生中")
    device.sh("input", "keyevent", "KEYCODE_BACK")
    device.node("ライブラリ")
    assert device.playing(local_title)
    print("PASS: mini-player, full player, pause/resume, seek, previous/next and back navigation", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--edge-cases", action="store_true")
    parser.add_argument("--player-ui", action="store_true")
    parser.add_argument("--screenshots", type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial):
        parser.error("Use a disposable emulator, not a personal device.")
    device = Android(args.adb, args.serial)
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
        device.tap("フォルダを選ぶ")
        device.tap("Music")
        device.tap(folder)
        device.tap("USE THIS FOLDER", "Use this folder")
        device.tap("Allow", "ALLOW")
        device.node(local_title)
        print("PASS: SAF picker and recursive local audio listing", flush=True)

        if args.player_ui:
            device.node("ライブラリ")
            device.tap("ライトに切替")
            device.node("ダークに切替")
            if args.screenshots:
                device.screenshot(args.screenshots / "library-light.png")
        device.sh("am", "force-stop", PACKAGE)
        device.start()
        device.node(local_title)
        print("PASS: persisted folder permission after process restart", flush=True)
        if args.player_ui:
            device.tap("ダークに切替")
            device.node("ライトに切替")
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
        message = device.node("保存しました：", "音声を取得できませんでした。", "保存できませんでした。", prefix=True, timeout=120)
        status = message.get("text", "")
        assert status.startswith("保存しました："), status
        title = status.removeprefix("保存しました：")
        assert title.endswith(".m4a"), title
        persisted = device.sh("ls", "-1", root).splitlines()
        assert title in persisted and not any(item.endswith(".partial") for item in persisted), persisted
        output = Path(work) / "saved.m4a"
        device.adb("pull", f"{root}/{title}", str(output))
        data = output.read_bytes()
        assert len(data) > 1024 and data[4:8] == b"ftyp", len(data)
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
                device.node("音声を取得できませんでした。", prefix=True, timeout=70)
                assert device.sh("ls", "-1", root).splitlines() == persisted
                print("PASS: offline import failure left existing files intact", flush=True)
        finally:
            device.sh("cmd", "connectivity", "airplane-mode", "disable")
            device.sh("svc", "wifi", "enable")

        if args.edge_cases:
            device.wait(lambda: 'Wifi is connected to' in device.sh("cmd", "wifi", "status"), "Wi-Fi reconnect")
            device.download(f"https://httpbingo.org/delay/10?probe={uuid.uuid4().hex}")
            device.tap("キャンセル")
            device.node("ダウンロードをキャンセルしました。")
            device.wait(lambda: "audio-" not in device.sh("run-as", PACKAGE, "ls", "cache"), "temporary download cleanup")
            assert device.sh("ls", "-1", root).splitlines() == persisted
            print("PASS: cancelled import left no incomplete files", flush=True)

            device.sh("mv", root, f"{root}-moved")
            try:
                device.tap("更新")
                device.node("フォルダを読み込めません。", prefix=True)
                print("PASS: moved folder reported a re-selection error", flush=True)
            finally:
                device.sh("mv", f"{root}-moved", root)


if __name__ == "__main__":
    main()
