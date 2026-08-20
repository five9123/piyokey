#!/usr/bin/env python3
"""Generate offline Korean CAF pronunciation without sending deck text externally."""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
DEFAULT_CATALOG = ROOT / "shared" / "mock_catalog"
MIN_CAF_BYTES = 4_096


def local_audio_items(catalog_root: Path) -> dict[str, str]:
    """Return every unique locally synthesized CAF path in fixture decks."""
    resolved: dict[str, str] = {}
    deck_roots = [catalog_root / "decks", catalog_root / "updates" / "decks"]
    for deck_root in deck_roots:
        for deck_path in sorted(deck_root.glob("*.json")):
            deck = json.loads(deck_path.read_text(encoding="utf-8"))
            for item in deck["items"]:
                relative_path = item.get("audio")
                if not relative_path:
                    raise RuntimeError(f"missing audio path: {deck_path.name} {item['id']}")
                path = Path(relative_path)
                if path.suffix == ".mp3":
                    continue
                if (
                    path.is_absolute()
                    or ".." in path.parts
                    or len(path.parts) != 2
                    or path.parts[0] != "audio"
                    or path.suffix != ".caf"
                ):
                    raise RuntimeError(f"unsafe local audio path: {relative_path}")
                existing = resolved.setdefault(relative_path, item["ko"])
                if existing != item["ko"]:
                    raise RuntimeError(f"audio hash collision: {relative_path}")
    if not resolved:
        raise RuntimeError(f"no local CAF audio references found below {catalog_root}")
    return resolved


def validate_caf(path: Path) -> None:
    if path.stat().st_size <= MIN_CAF_BYTES:
        raise RuntimeError(f"local speech produced an empty audio payload: {path}")
    afinfo = shutil.which("afinfo")
    if afinfo is None:
        return
    result = subprocess.run(
        [afinfo, str(path)],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,
        text=True,
    )
    if result.returncode != 0:
        detail = result.stderr.strip() or "CoreAudio could not decode the file"
        raise RuntimeError(f"invalid CAF {path}: {detail}")


def generate_local_speech(target: Path, text: str, *, voice: str) -> None:
    say = shutil.which("say")
    if say is None:
        raise RuntimeError("macOS say command is required for local speech generation")
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(".partial.caf")
    try:
        temporary.unlink(missing_ok=True)
        subprocess.run(
            [
                say,
                "-v",
                voice,
                "-o",
                str(temporary),
                "--file-format=caff",
                "--data-format=ima4",
                text,
            ],
            check=True,
        )
        validate_caf(temporary)
        os.replace(temporary, target)
    finally:
        temporary.unlink(missing_ok=True)


def prune_stale_assets(catalog_root: Path, referenced: set[str]) -> int:
    audio_root = catalog_root / "audio"
    removed = 0
    for candidate in audio_root.glob("ko_*.caf"):
        relative_path = candidate.relative_to(catalog_root).as_posix()
        if relative_path not in referenced:
            candidate.unlink()
            removed += 1
    return removed


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog-root", type=Path, default=DEFAULT_CATALOG)
    parser.add_argument("--voice", default="Yuna")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--prune", action="store_true")
    args = parser.parse_args()

    catalog_root = args.catalog_root.resolve()
    items = local_audio_items(catalog_root)
    generated = 0
    for index, (relative_path, korean) in enumerate(sorted(items.items()), start=1):
        target = catalog_root / relative_path
        if target.exists() and not args.force:
            validate_caf(target)
            continue
        generate_local_speech(target, korean, voice=args.voice)
        generated += 1
        print(f"[{index}/{len(items)}] {korean} -> {relative_path}", flush=True)

    removed = prune_stale_assets(catalog_root, set(items)) if args.prune else 0
    total_bytes = sum((catalog_root / path).stat().st_size for path in items)
    print(
        f"local speech assets: {len(items)} unique, {generated} generated, "
        f"{removed} stale removed, {total_bytes / 1024 / 1024:.2f} MiB"
    )


if __name__ == "__main__":
    main()
