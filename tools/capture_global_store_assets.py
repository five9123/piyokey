#!/usr/bin/env python3
"""Capture current ja/en/es/de/fr simulator UI and footage; never uploads or submits."""
from __future__ import annotations

import argparse
import json
import re
import signal
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def capture(language: str, device: str, output: Path) -> None:
    folder = output / language
    folder.mkdir(parents=True, exist_ok=False)
    recording = subprocess.Popen(
        ["xcrun", "simctl", "io", device, "recordVideo", "--codec=h264", "--mask=ignored", str(folder / "capture.mp4")],
        stderr=subprocess.PIPE, stdout=subprocess.DEVNULL, text=True,
    )
    ready = False
    try:
        for line in recording.stderr:
            if "Recording started" in line:
                ready = True
                break
        if not ready:
            raise RuntimeError("Simulator recording did not start")
        recording_epoch = time.time()
        command = [
            "xcodebuild", "test-without-building", "-project", "ios/Hanco/Hanco.xcodeproj",
            "-scheme", "Hanco", "-destination", f"platform=iOS Simulator,id={device}",
            "-derivedDataPath", str(ROOT / "artifacts/store-localization/DerivedData"),
            "-parallel-testing-enabled", "NO", "-resultBundlePath", str(folder / "capture.xcresult"),
            f"-only-testing:HancoUITests/HancoUITests/testAppStoreScreenshotGlobal{language.upper()}",
        ]
        markers = {}
        with (folder / "capture.log").open("w") as log:
            process = subprocess.Popen(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
            for line in process.stdout:
                log.write(line)
                match = re.search(r"STORE_SCENE (\S+) ([0-9.]+)", line)
                if match:
                    markers[match[1]] = float(match[2]) - recording_epoch
                    print(f"{language}: scene {match[1]} @ {markers[match[1]]:.2f}s", flush=True)
                elif "error:" in line or "failed" in line or "passed" in line:
                    print(line.rstrip(), flush=True)
            status = process.wait()
    finally:
        recording.send_signal(signal.SIGINT)
        recording.wait(timeout=60)
    (folder / "timing.json").write_text(json.dumps({
        "recording_epoch": recording_epoch, "scenes": markers,
        "source": "capture.mp4", "test_exit_code": status,
    }, indent=2) + "\n")
    subprocess.run(["xcrun", "xcresulttool", "export", "attachments", "--path", str(folder / "capture.xcresult"),
                    "--output-path", str(folder / "attachments")], check=True)
    if status:
        raise RuntimeError(f"Capture UI test failed ({language}); inspect capture.log")
    print(f"{language}: capture complete", flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--languages", nargs="+", choices=["ja", "en", "es", "de", "fr"], default=["ja", "en", "es", "de", "fr"])
    parser.add_argument("--device", default="00608B21-6BB5-4F22-BDD2-9A4F67DA4C59")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    for language in args.languages:
        capture(language, args.device, args.output.resolve())


if __name__ == "__main__":
    main()
