#!/usr/bin/env python3
"""Claim the current Git worktree for one PIYOKEY issue."""

from __future__ import annotations

import argparse
import json
import subprocess
from datetime import datetime, timezone
from pathlib import Path


OWNER_FILENAME = "piyokey-owner.json"


def git_value(*args: str) -> str:
    result = subprocess.run(
        ("git", *args), check=True, capture_output=True, text=True
    )
    return result.stdout.strip()


def metadata_path() -> Path:
    return Path(git_value("rev-parse", "--absolute-git-dir")) / OWNER_FILENAME


def expected_issue(branch: str) -> int | None:
    prefix = "codex/"
    if not branch.startswith(prefix):
        return None
    number = branch.removeprefix(prefix).split("-", 1)[0]
    return int(number) if number.isdigit() else None


def claim(issue: int, owner: str, force: bool = False) -> Path:
    if issue <= 0:
        raise ValueError("issue must be a positive integer")
    if not owner:
        raise ValueError("owner must not be empty")
    branch = git_value("branch", "--show-current")
    worktree = git_value("rev-parse", "--show-toplevel")
    branch_issue = expected_issue(branch)
    if branch_issue != issue:
        raise ValueError(
            f"branch {branch!r} must start with codex/{issue}- before it can be claimed"
        )

    destination = metadata_path()
    if destination.exists() and not force:
        current = json.loads(destination.read_text(encoding="utf-8"))
        if current.get("issue") != issue or current.get("owner") != owner:
            raise FileExistsError(
                f"worktree is already claimed by issue {current.get('issue')} "
                f"and owner {current.get('owner')}; use --force only after handoff"
            )

    record = {
        "schema_version": 1,
        "issue": issue,
        "owner": owner,
        "branch": branch,
        "worktree": worktree,
        "claimed_at": datetime.now(timezone.utc).isoformat(),
    }
    destination.write_text(
        json.dumps(record, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    return destination


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    claim_parser = subparsers.add_parser("claim")
    claim_parser.add_argument("--issue", required=True, type=int)
    claim_parser.add_argument("--owner", required=True)
    claim_parser.add_argument("--force", action="store_true")

    subparsers.add_parser("show")
    args = parser.parse_args()

    if args.command == "claim":
        try:
            destination = claim(args.issue, args.owner.strip(), args.force)
        except (FileExistsError, ValueError) as error:
            parser.error(str(error))
        print(destination)
        return 0

    destination = metadata_path()
    if not destination.exists():
        print("unclaimed")
        return 1
    print(destination.read_text(encoding="utf-8"), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
