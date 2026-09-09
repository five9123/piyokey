#!/usr/bin/env python3
"""Historical reproduction of availability blocking, using mocked GameKit.

The production service is unchanged. These scenarios do NOT establish what
GameKit returned on the user's device. No real account or scores are touched.
"""
import hashlib
from pathlib import Path
import subprocess
import tempfile

from reproduce import EXPECTED_SHA256, HERE, SOURCE


def main():
    data = SOURCE.read_bytes()
    if hashlib.sha256(data).hexdigest() != EXPECTED_SHA256:
        raise SystemExit("Service changed since audit; review before rerunning.")
    source = "\n".join(
        line for line in data.decode().splitlines()
        if line not in {"import Combine", "import GameKit", "import UIKit"}
    )
    stubs = (HERE / "stubs.swift").read_text()
    old = "  completionHandler((IDs ?? GameCenterLeaderboard.allCases.map(\\.rawValue)).map(GKLeaderboard.init),nil)"
    new = """  if IDs == nil {
   fullListLoadCount += 1
   completionHandler(fullListOverride, nil)
  } else {
   completionHandler(IDs!.map(GKLeaderboard.init), nil)
  }"""
    assert stubs.count(old) == 1
    stubs = stubs.replace(old, new).replace(
        " static var submitted:",
        " static var fullListOverride: [GKLeaderboard] = []\n"
        " static var fullListLoadCount = 0\n static var submitted:",
    )
    with tempfile.TemporaryDirectory(prefix="piyokey-availability-repro-") as directory:
        work = Path(directory)
        (work / "Service.swift").write_text(source)
        (work / "stubs.swift").write_text(stubs)
        subprocess.run([
            "xcrun", "swiftc", "-swift-version", "5", "-parse-as-library",
            "-module-cache-path", str(work / "cache"), str(work / "stubs.swift"),
            str(work / "Service.swift"), str(HERE / "availability_probe.swift"),
            "-o", str(work / "reproduce"),
        ], check=True)
        subprocess.run([str(work / "reproduce")], check=True)


if __name__ == "__main__":
    main()
