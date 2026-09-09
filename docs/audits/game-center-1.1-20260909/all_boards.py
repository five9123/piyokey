#!/usr/bin/env python3
"""Audit every shipping leaderboard with original service and mock GameKit.

Exit success means audit assertions matched, including reproduced defects;
it does not mean the product is correct or real device submissions succeeded.
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
    assert stubs.count(old) == 1
    stubs = stubs.replace(old, """  if IDs == nil {
   fullListLoadCount += 1
   if holdFullList { return }
   completionHandler(fullListOverride, fullListError)
  } else {
   completionHandler(IDs!.map(GKLeaderboard.init), nil)
  }""").replace(
        " static var submitted:",
        " static var fullListOverride: [GKLeaderboard] = []\n"
        " static var fullListError: Error?\n"
        " static var holdFullList = false\n"
        " static var fullListLoadCount = 0\n static var submitted:",
    )
    with tempfile.TemporaryDirectory(prefix="piyokey-all-boards-") as directory:
        work = Path(directory)
        (work / "Service.swift").write_text(source)
        (work / "stubs.swift").write_text(stubs)
        subprocess.run([
            "xcrun", "swiftc", "-swift-version", "5", "-parse-as-library",
            "-module-cache-path", str(work / "cache"), str(work / "stubs.swift"),
            str(work / "Service.swift"), str(HERE / "all_boards.swift"),
            "-o", str(work / "reproduce"),
        ], check=True)
        result = subprocess.run([str(work / "reproduce")], check=True, capture_output=True, text=True)
        print(result.stdout, end="")


if __name__ == "__main__":
    main()
