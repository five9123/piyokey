#!/usr/bin/env python3
"""Generate offline gTTS Korean pronunciation for every app-provided prompt.

The generator scans every catalog and game-preset deck recursively, adds the
non-deck prompts listed in ``pronunciation_prompts.json``, resolves duplicates to
one content-addressed MP3, and validates each download with macOS CoreAudio before
replacing an existing asset. The iOS app only plays the generated files offline.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import time
from pathlib import Path
from typing import Callable


ROOT = Path(__file__).resolve().parent.parent
DEFAULT_CATALOG = ROOT / "shared" / "mock_catalog"
MIN_MP3_BYTES = 1_024


def audio_asset_path(korean: str) -> str:
    """Return the canonical content-addressed MP3 path for a Korean prompt."""
    digest = hashlib.sha256(korean.encode("utf-8")).hexdigest()[:20]
    return f"audio/ko_{digest}.mp3"


def audio_items(
    catalog_root: Path,
    *,
    require_prompt_manifest: bool = False,
) -> dict[str, str]:
    """Return every unique app-provided gTTS path and Korean prompt."""
    resolved: dict[str, str] = {}
    deck_roots = [catalog_root / "decks", catalog_root / "updates" / "decks"]
    for deck_root in deck_roots:
        for deck_path in sorted(deck_root.rglob("*.json")):
            deck = json.loads(deck_path.read_text(encoding="utf-8"))
            for item in deck["items"]:
                relative_path = item.get("audio")
                if not relative_path:
                    raise RuntimeError(f"missing audio path: {deck_path.name} {item['id']}")
                path = Path(relative_path)
                if (
                    path.is_absolute()
                    or ".." in path.parts
                    or len(path.parts) != 2
                    or path.parts[0] != "audio"
                    or path.suffix != ".mp3"
                ):
                    raise RuntimeError(f"unsafe gTTS audio path: {relative_path}")
                existing = resolved.setdefault(relative_path, item["ko"])
                if existing != item["ko"]:
                    raise RuntimeError(f"audio hash collision: {relative_path}")
                expected_path = audio_asset_path(item["ko"])
                if relative_path != expected_path:
                    raise RuntimeError(
                        f"non-canonical gTTS audio path: {relative_path}; expected {expected_path}"
                    )

    prompt_manifest = catalog_root / "pronunciation_prompts.json"
    if prompt_manifest.exists():
        payload = json.loads(prompt_manifest.read_text(encoding="utf-8"))
        if set(payload) != {"schema_version", "prompts"} or payload["schema_version"] != 1:
            raise RuntimeError(f"invalid pronunciation prompt manifest: {prompt_manifest}")
        prompts = payload["prompts"]
        if (
            not isinstance(prompts, list)
            or not prompts
            or any(not isinstance(prompt, str) or not prompt.strip() for prompt in prompts)
            or len(set(prompts)) != len(prompts)
        ):
            raise RuntimeError(f"invalid pronunciation prompts: {prompt_manifest}")
        for prompt in prompts:
            relative_path = audio_asset_path(prompt)
            existing = resolved.setdefault(relative_path, prompt)
            if existing != prompt:
                raise RuntimeError(f"audio hash collision: {relative_path}")
    elif require_prompt_manifest:
        raise RuntimeError(f"missing pronunciation prompt manifest: {prompt_manifest}")
    if not resolved:
        raise RuntimeError(f"no pronunciation prompts found below {catalog_root}")
    return resolved


def validate_mp3(path: Path) -> None:
    if path.stat().st_size <= MIN_MP3_BYTES:
        raise RuntimeError(f"gTTS produced an empty audio payload: {path}")
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
        raise RuntimeError(f"invalid MP3 {path}: {detail}")


def generate_gtts(
    target: Path,
    text: str,
    *,
    lang: str,
    tld: str,
    slow: bool,
    attempts: int,
    tts_factory: Callable[..., object] | None = None,
) -> None:
    if tts_factory is None:
        try:
            from gtts import gTTS
        except ImportError as error:
            raise RuntimeError(
                "gTTS is not installed; run "
                "python3 -m pip install -r tools/requirements-audio.txt"
            ) from error
        tts_factory = gTTS

    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(".partial.mp3")
    last_error: Exception | None = None
    try:
        for attempt in range(1, attempts + 1):
            temporary.unlink(missing_ok=True)
            try:
                speech = tts_factory(text=text, lang=lang, tld=tld, slow=slow)
                speech.save(str(temporary))  # type: ignore[attr-defined]
                validate_mp3(temporary)
                os.replace(temporary, target)
                return
            except Exception as error:  # gTTS wraps several request-layer errors.
                last_error = error
                if attempt < attempts:
                    time.sleep(2 ** (attempt - 1))
        raise RuntimeError(
            f"gTTS failed after {attempts} attempts for {text!r}: {last_error}"
        ) from last_error
    finally:
        temporary.unlink(missing_ok=True)


def prune_stale_assets(catalog_root: Path, referenced: set[str]) -> int:
    audio_root = catalog_root / "audio"
    removed = 0
    for pattern in ("ko_*.mp3", "ko_*.caf"):
        for candidate in audio_root.glob(pattern):
            relative_path = candidate.relative_to(catalog_root).as_posix()
            if relative_path not in referenced:
                candidate.unlink()
                removed += 1
    return removed


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog-root", type=Path, default=DEFAULT_CATALOG)
    parser.add_argument("--lang", default="ko")
    parser.add_argument("--tld", default="com")
    parser.add_argument("--slow", action="store_true")
    parser.add_argument("--attempts", type=int, default=3)
    parser.add_argument("--delay", type=float, default=0.1)
    parser.add_argument("--force", action="store_true")
    parser.add_argument(
        "--prune",
        action="store_true",
        help="remove unreferenced content-addressed MP3 and legacy CAF assets after success",
    )
    args = parser.parse_args()
    if args.attempts < 1:
        parser.error("--attempts must be at least 1")
    if args.delay < 0:
        parser.error("--delay cannot be negative")

    catalog_root = args.catalog_root.resolve()
    items = audio_items(catalog_root, require_prompt_manifest=True)
    generated = 0
    for index, (relative_path, korean) in enumerate(sorted(items.items()), start=1):
        target = catalog_root / relative_path
        if target.exists() and not args.force:
            validate_mp3(target)
            continue
        generate_gtts(
            target,
            korean,
            lang=args.lang,
            tld=args.tld,
            slow=args.slow,
            attempts=args.attempts,
        )
        generated += 1
        print(f"[{index}/{len(items)}] {korean} -> {relative_path}", flush=True)
        if args.delay:
            time.sleep(args.delay)

    removed = prune_stale_assets(catalog_root, set(items)) if args.prune else 0
    total_bytes = sum((catalog_root / path).stat().st_size for path in items)
    print(
        f"gTTS audio assets: {len(items)} unique, {generated} generated, "
        f"{removed} stale removed, {total_bytes / 1024 / 1024:.2f} MiB"
    )


if __name__ == "__main__":
    main()
