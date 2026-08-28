#!/usr/bin/env python3
"""Create immutable, commit-addressed local verification evidence."""

from __future__ import annotations

import argparse
import json
import subprocess
from datetime import datetime, timezone
from pathlib import Path


VALID_STATUSES = {"pass", "fail", "skip"}


def git_value(*args: str) -> str:
    result = subprocess.run(
        ("git", *args), check=True, capture_output=True, text=True
    )
    return result.stdout.strip()


def parse_check(value: str) -> dict[str, str]:
    parts = value.split("::", 3)
    if len(parts) < 3:
        raise ValueError("check must be NAME::STATUS::COMMAND[::NOTE]")
    name, status, command = (part.strip() for part in parts[:3])
    note = parts[3].strip() if len(parts) == 4 else ""
    if not name or not command or status not in VALID_STATUSES:
        raise ValueError("check name/command is required and status is pass, fail, or skip")
    return {"name": name, "status": status, "command": command, "note": note}


def build_record(
    *,
    scope: str,
    commit: str,
    tree: str,
    branch: str,
    checks: list[dict[str, str]],
    manual_gates: list[str],
) -> dict[str, object]:
    result = "fail" if any(check["status"] == "fail" for check in checks) else "pass"
    return {
        "schema_version": 1,
        "verified_commit": commit,
        "verified_tree": tree,
        "branch": branch,
        "scope": scope,
        "result": result,
        "recorded_at": datetime.now(timezone.utc).isoformat(),
        "checks": checks,
        "manual_gates": manual_gates,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scope", required=True)
    parser.add_argument(
        "--check",
        action="append",
        required=True,
        help="NAME::STATUS::COMMAND[::NOTE]",
    )
    parser.add_argument("--manual-gate", action="append", default=[])
    parser.add_argument("--verified-ref", default="HEAD")
    parser.add_argument("--output-dir", default="release/evidence")
    args = parser.parse_args()

    if git_value("status", "--porcelain"):
        parser.error("verification evidence can only be recorded from a clean worktree")

    try:
        checks = [parse_check(value) for value in args.check]
    except ValueError as error:
        parser.error(str(error))

    commit = git_value("rev-parse", args.verified_ref)
    tree = git_value("rev-parse", f"{args.verified_ref}^{{tree}}")
    branch = git_value("branch", "--show-current") or "HEAD"
    record = build_record(
        scope=args.scope,
        commit=commit,
        tree=tree,
        branch=branch,
        checks=checks,
        manual_gates=args.manual_gate,
    )
    destination = Path(args.output_dir) / f"{commit}.json"
    if destination.exists():
        parser.error(f"evidence already exists: {destination}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(
        json.dumps(record, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(destination)
    return 1 if record["result"] == "fail" else 0


if __name__ == "__main__":
    raise SystemExit(main())
