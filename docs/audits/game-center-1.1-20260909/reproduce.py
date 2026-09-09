#!/usr/bin/env python3
"""Reproduce audited GameCenterService behavior with local GameKit test doubles.

No device access, account authentication, or network score submission is performed.
The original service implementation is copied with only framework imports removed.
"""
import hashlib
from pathlib import Path
import subprocess
import tempfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
SOURCE = ROOT / "ios/Hanco/Hanco/Core/GameCenter/GameCenterService.swift"
EXPECTED_SHA256 = "f37e33744f345f6a7d91e5287427b274ed50438e09cff6e5ccf9edb54dcb2c43"


def main():
    data = SOURCE.read_bytes()
    if hashlib.sha256(data).hexdigest() != EXPECTED_SHA256:
        raise SystemExit("Service changed since audit; review the fixture before rerunning.")
    source = "\n".join(
        line for line in data.decode().splitlines()
        if line not in {"import Combine", "import GameKit", "import UIKit"}
    )
    with tempfile.TemporaryDirectory(prefix="piyokey-gc-repro-") as directory:
        work = Path(directory)
        (work / "Service.swift").write_text(source)
        subprocess.run([
            "xcrun", "swiftc", "-swift-version", "5", "-parse-as-library",
            "-module-cache-path", str(work / "cache"), str(HERE / "stubs.swift"),
            str(work / "Service.swift"), str(HERE / "main.swift"),
            "-o", str(work / "reproduce"),
        ], check=True)
        subprocess.run([str(work / "reproduce")], check=True)


if __name__ == "__main__":
    main()
