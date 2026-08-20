#!/usr/bin/env python3
"""Generate evaluation-only Korean audio assets for the official fixture decks.

This pilot uses the locally installed macOS Korean voice so packaging, offline
playback, and bundled size can be tested without sending content externally.
It is not the production content pipeline; release assets must be regenerated
with an officially supported TTS provider.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
DEFAULT_CATALOG = ROOT / "shared" / "mock_catalog"
CAF_HEADER_BYTES = 4096


def audio_items(catalog_root: Path) -> dict[str, str]:
    resolved: dict[str, str] = {}
    deck_roots = [catalog_root / "decks", catalog_root / "updates" / "decks"]
    for deck_root in deck_roots:
        for deck_path in sorted(deck_root.glob("official_*.json")):
            deck = json.loads(deck_path.read_text(encoding="utf-8"))
            for item in deck["items"]:
                relative_path = item.get("audio")
                if not relative_path:
                    raise RuntimeError(f"missing audio path: {deck_path.name} {item['id']}")
                if (
                    Path(relative_path).is_absolute()
                    or ".." in Path(relative_path).parts
                    or not relative_path.startswith("audio/")
                    or not relative_path.endswith(".caf")
                ):
                    raise RuntimeError(f"unsafe audio path: {relative_path}")
                existing = resolved.setdefault(relative_path, item["ko"])
                if existing != item["ko"]:
                    raise RuntimeError(f"audio hash collision: {relative_path}")
    return resolved


def generate_local_voice(target: Path, text: str, voice: str) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(".partial.caf")
    try:
        subprocess.run(
            [
                "say",
                "-v",
                voice,
                "-o",
                str(temporary),
                "--file-format=caff",
                "--data-format=ulaw@22050",
                text,
            ],
            check=True,
        )
        if temporary.stat().st_size <= CAF_HEADER_BYTES:
            raise RuntimeError(
                f"local voice produced an empty audio payload for {text!r}: {temporary}"
            )
        os.replace(temporary, target)
    finally:
        temporary.unlink(missing_ok=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog-root", type=Path, default=DEFAULT_CATALOG)
    parser.add_argument("--voice", default="Yuna")
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    catalog_root = args.catalog_root.resolve()
    items = audio_items(catalog_root)
    generated = 0
    for relative_path, korean in sorted(items.items()):
        target = catalog_root / relative_path
        if target.exists() and target.stat().st_size > CAF_HEADER_BYTES and not args.force:
            continue
        generate_local_voice(target, korean, args.voice)
        generated += 1

    total_bytes = sum((catalog_root / path).stat().st_size for path in items)
    print(
        f"audio assets: {len(items)} unique, {generated} generated, "
        f"{total_bytes / 1024 / 1024:.2f} MiB"
    )


if __name__ == "__main__":
    main()
