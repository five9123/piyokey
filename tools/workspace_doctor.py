#!/usr/bin/env python3
"""Read-only checks for a PIYOKEY clone or worktree."""

from __future__ import annotations

import argparse
import json
import os
import platform
import re
import shutil
import subprocess
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Optional, Sequence


ROOT = Path(__file__).resolve().parents[1]
EXPECTED_REMOTE = "github.com/five9123-maker/piyokey"
REQUIRED_FILES = (
    "AGENTS.md",
    "PRD.md",
    "DECISIONS.md",
    "shared/test_vectors.json",
)


@dataclass(frozen=True)
class Check:
    name: str
    status: str
    detail: str


def normalize_remote(value: str) -> str:
    remote = value.strip().removesuffix(".git")
    if remote.startswith("git@github.com:"):
        remote = "github.com/" + remote.removeprefix("git@github.com:")
    for prefix in ("https://", "http://", "ssh://git@"):
        if remote.startswith(prefix):
            remote = remote.removeprefix(prefix)
    return remote.rstrip("/")


def branch_status(branch: str) -> tuple[str, str]:
    if not branch or branch == "HEAD":
        return "FAIL", "detached HEAD에서는 작업하지 않습니다"
    if branch == "main":
        return "WARN", "편집 전 issue 전용 branch 또는 worktree를 만드세요"
    return "PASS", branch


def python_version_status(version: Sequence[int]) -> tuple[str, str]:
    rendered = ".".join(str(part) for part in version[:3])
    if tuple(version[:2]) < (3, 11):
        return "FAIL", f"Python {rendered}; 3.11 이상이 필요합니다"
    return "PASS", f"Python {rendered}"


def java_version_status(output: str) -> tuple[str, str]:
    match = re.search(r'version "(?:1\.)?(\d+)(?:[._](\d+))?', output)
    if match is None:
        return "FAIL", "Java 버전을 확인할 수 없습니다"
    major = int(match.group(1))
    rendered = match.group(0).removeprefix("version ").strip('"')
    if major < 17:
        return "FAIL", f"Java {rendered}; JDK 17 이상이 필요합니다"
    return "PASS", f"Java {rendered}"


def android_sdk_path(environment: dict[str, str], home: Path) -> Optional[Path]:
    configured = environment.get("ANDROID_HOME") or environment.get("ANDROID_SDK_ROOT")
    candidates = [Path(configured).expanduser()] if configured else []
    candidates.append(home / "Library/Android/sdk")
    return next((candidate for candidate in candidates if candidate.is_dir()), None)


def android_platform_path(sdk: Optional[Path], api_level: int) -> Optional[Path]:
    if sdk is None:
        return None
    platforms = sdk / "platforms"
    candidates = [platforms / f"android-{api_level}"]
    candidates.extend(sorted(platforms.glob(f"android-{api_level}.*")))
    return next((candidate for candidate in candidates if candidate.is_dir()), None)


def run(*args: str, cwd: Path = ROOT) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args,
        cwd=cwd,
        check=False,
        capture_output=True,
        text=True,
        timeout=15,
    )


def git_value(*args: str) -> str:
    result = run("git", *args)
    return result.stdout.strip() if result.returncode == 0 else ""


def collect_checks(scope: str) -> list[Check]:
    checks: list[Check] = []

    root = git_value("rev-parse", "--show-toplevel")
    if root and Path(root).resolve() == ROOT.resolve():
        checks.append(Check("repository", "PASS", str(ROOT)))
    else:
        checks.append(Check("repository", "FAIL", "PIYOKEY Git 작업 공간이 아닙니다"))

    remote = git_value("remote", "get-url", "origin")
    normalized = normalize_remote(remote)
    remote_status = "PASS" if normalized == EXPECTED_REMOTE else "FAIL"
    checks.append(Check("origin", remote_status, remote or "origin이 없습니다"))

    branch = git_value("branch", "--show-current")
    status, detail = branch_status(branch)
    checks.append(Check("branch", status, detail))

    dirty = git_value("status", "--porcelain")
    checks.append(
        Check(
            "working_tree",
            "WARN" if dirty else "PASS",
            "커밋되지 않은 변경이 있습니다" if dirty else "clean",
        )
    )

    status, detail = python_version_status(sys.version_info)
    checks.append(Check("python", status, detail))

    missing = [relative for relative in REQUIRED_FILES if not (ROOT / relative).exists()]
    checks.append(
        Check(
            "contracts",
            "FAIL" if missing else "PASS",
            "누락: " + ", ".join(missing) if missing else "필수 문서·공용 벡터 확인",
        )
    )

    pull_ff = git_value("config", "--get", "pull.ff")
    checks.append(
        Check(
            "pull.ff",
            "PASS" if pull_ff == "only" else "WARN",
            pull_ff or "git config pull.ff only 권장",
        )
    )
    fetch_prune = git_value("config", "--get", "fetch.prune")
    checks.append(
        Check(
            "fetch.prune",
            "PASS" if fetch_prune == "true" else "WARN",
            fetch_prune or "git config fetch.prune true 권장",
        )
    )

    gh = shutil.which("gh")
    if gh is None:
        checks.append(Check("github_cli", "WARN", "gh가 없어 PR/issue CLI 작업을 건너뜁니다"))
    else:
        auth = run(gh, "auth", "status")
        checks.append(
            Check(
                "github_cli",
                "PASS" if auth.returncode == 0 else "WARN",
                "인증됨" if auth.returncode == 0 else "gh auth login이 필요합니다",
            )
        )

    if scope == "ios":
        if platform.system() != "Darwin":
            checks.append(Check("ios_host", "FAIL", "iOS 작업에는 macOS가 필요합니다"))
        else:
            checks.append(Check("ios_host", "PASS", platform.mac_ver()[0] or "macOS"))
        for command in ("xcodebuild", "swift"):
            path = shutil.which(command)
            checks.append(
                Check(command, "PASS" if path else "FAIL", path or f"{command}가 없습니다")
            )
        project = ROOT / "ios/Hanco/Hanco.xcodeproj"
        checks.append(
            Check(
                "ios_project",
                "PASS" if project.exists() else "FAIL",
                str(project),
            )
        )

    if scope == "android":
        java = shutil.which("java")
        if java is None:
            checks.append(Check("java", "FAIL", "JDK 17 이상이 필요합니다"))
        else:
            result = run(java, "-version")
            status, detail = java_version_status(result.stdout + result.stderr)
            checks.append(Check("java", status, detail))

        sdk = android_sdk_path(dict(os.environ), Path.home())
        checks.append(
            Check(
                "android_sdk",
                "PASS" if sdk else "FAIL",
                str(sdk) if sdk else "ANDROID_HOME 또는 API 37 SDK가 필요합니다",
            )
        )
        api_37 = android_platform_path(sdk, 37)
        checks.append(
            Check(
                "android_api_37",
                "PASS" if api_37 and api_37.is_dir() else "FAIL",
                str(api_37) if api_37 else "Android API 37이 없습니다",
            )
        )

        wrapper = ROOT / "android/gradlew"
        checks.append(
            Check(
                "gradle_wrapper",
                "PASS" if wrapper.is_file() and os.access(wrapper, os.X_OK) else "FAIL",
                str(wrapper),
            )
        )
        required_modules = (
            "android/app/build.gradle.kts",
            "android/core/hangul/build.gradle.kts",
            "android/core/deckkit/build.gradle.kts",
            "android/core/piyodeck/build.gradle.kts",
        )
        missing_modules = [
            relative for relative in required_modules if not (ROOT / relative).is_file()
        ]
        checks.append(
            Check(
                "android_modules",
                "FAIL" if missing_modules else "PASS",
                "누락: " + ", ".join(missing_modules)
                if missing_modules
                else "app·hangul·deckkit·piyodeck 확인",
            )
        )

    return checks


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scope", choices=("repo", "ios", "android"), default="repo")
    parser.add_argument("--json", action="store_true", dest="as_json")
    args = parser.parse_args()

    checks = collect_checks(args.scope)
    if args.as_json:
        print(json.dumps([asdict(check) for check in checks], ensure_ascii=False, indent=2))
    else:
        for check in checks:
            print(f"[{check.status}] {check.name}: {check.detail}")

    return 1 if any(check.status == "FAIL" for check in checks) else 0


if __name__ == "__main__":
    raise SystemExit(main())
