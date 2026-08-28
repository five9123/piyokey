#!/usr/bin/env python3
"""Audit a local ten-market delivery and optionally package final media only."""
from __future__ import annotations

import argparse
import hashlib
import json
import zipfile
from datetime import datetime, timezone
from html.parser import HTMLParser
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[1]


class LinkAudit(HTMLParser):
    def __init__(self, directory):
        super().__init__()
        self.directory = directory

    def handle_starttag(self, tag, attrs):
        for key, value in attrs:
            if key in {"href", "src", "poster"} and value and not value.startswith("#"):
                assert (self.directory / value).is_file(), value


def captions_overview(folder, locales):
    for group in range(2):
        selected = locales[group * 5:(group + 1) * 5]
        canvas = Image.new("RGB", (1080, len(selected) * 260), "#eee8ef")
        draw = ImageDraw.Draw(canvas)
        for row, locale in enumerate(selected):
            code = locale["locale"]
            draw.text((8, row * 260 + 6), code, fill="#30203b")
            with Image.open(folder / code / "preview-contact.png") as sheet:
                assert sheet.size == (1800, 2496), sheet.size
                for index, cell in enumerate([1, 4, 6, 9, 11, 12]):
                    x, y = cell % 5 * 360, cell // 5 * 832
                    top = 70 if index < 5 else 610
                    crop = sheet.crop((x, y + top, x + 360, y + top + 110))
                    canvas.paste(crop, ((index % 3) * 360, row * 260 + 30 + (index // 3) * 110))
        canvas.save(folder / f"video-caption-qa-{group+1}.png")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folder", type=Path)
    parser.add_argument("--package", action="store_true")
    parser.add_argument("--agent-reviewed", action="store_true", help="Only after inspecting all screenshot sheets and preview caption sheets")
    parser.add_argument("--compare-package", type=Path, help="Confirm PNG changes are confined to the support-language note band")
    args = parser.parse_args()
    folder = args.folder.resolve()
    data = json.loads((folder / "manifest.json").read_text())
    copy = json.loads((ROOT / "release/store-assets/localizations.json").read_text())
    assert len(data["locales"]) == 10
    assert data["copy_sha256"] == hashlib.sha256((ROOT / "release/store-assets/localizations.json").read_bytes()).hexdigest()
    files = [folder / "index.html", folder / "manifest.json"]
    size_bytes = 0
    changed_screenshots = 0
    for locale, loc_copy in zip(data["locales"], copy["locales"]):
        code = locale["locale"]
        assert code == loc_copy["locale"]
        assert len(locale["screenshots"]) == 10
        specs = json.loads((folder / code / "screenshot-spec.json").read_text())
        assert [[s["title"], s["subtitle"]] for s in specs["specs"]] == loc_copy["screenshots"]
        assert specs["disclosure"] == loc_copy["disclosure"] == ""
        clips = json.loads((folder / code / "preview-spec.json").read_text())
        assert [clip["caption"] for clip in clips] == loc_copy["preview"]
        assert all(clip["detail"] is None for clip in clips[:-1])
        assert clips[-1]["detail"] == loc_copy["purchase_disclosure"]
        for asset in locale["screenshots"] + [locale["preview"]]:
            path = ROOT / asset["path"]
            assert path.is_file()
            assert hashlib.sha256(path.read_bytes()).hexdigest() == asset["sha256"], str(path)
            files.append(path)
            size_bytes += path.stat().st_size
            if path.suffix == ".png":
                with Image.open(path) as image:
                    assert image.size == (1320, 2868) and image.mode == "RGB"
                    if args.compare_package:
                        with zipfile.ZipFile(args.compare_package) as previous_zip:
                            with previous_zip.open(str(path.relative_to(folder))) as entry:
                                with Image.open(entry) as previous:
                                    changed = ImageChops.difference(image, previous.convert("RGB")).getbbox()
                        if changed:
                            assert changed[1] >= 570 and changed[3] <= 635, (code, changed)
                            changed_screenshots += 1
            else:
                assert path.stat().st_size < 500_000_000
        validation = (folder / code / "video-validation.txt").read_text()
        for line in ["duration=26.400", "size=886x1920", "nominal_fps=30.000", "video_codec=h264", "audio_channels=2", "audio_sample_rate=48000", "decoded_frames=792", "decode_status=completed"]:
            assert line in validation, (code, line)
        files += [folder / code / "manifest.json", folder / code / "video-validation.txt"]
    LinkAudit(folder).feed((folder / "index.html").read_text())
    captions_overview(folder, data["locales"])
    if args.agent_reviewed:
        for locale in data["locales"]:
            locale["visual_review"] = "agent_contact_sheets_reviewed_native_human_review_pending"
            (folder / locale["locale"] / "manifest.json").write_text(json.dumps(locale, ensure_ascii=False, indent=2) + "\n")
        (folder / "manifest.json").write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    report = {"verified_at": datetime.now(timezone.utc).isoformat(), "screenshots": 100, "previews": 10,
              "media_bytes": size_bytes, "checksum_verification": "pass", "png_dimensions_and_rgb": "pass",
              "video_full_decode": "pass", "html_local_links": "pass", "uploaded": False,
              "release_candidate_match": "pending", "native_human_translation_review": "pending",
              "agent_visual_review": "contact_sheets_reviewed" if args.agent_reviewed else "pending"}
    if args.compare_package:
        report["changed_screenshots"] = changed_screenshots
        report["changes_confined_to_support_note_band"] = "pass"
    (folder / "verification.json").write_text(json.dumps(report, indent=2) + "\n")
    files.append(folder / "verification.json")
    if args.package:
        package = folder.parent / "typee-app-store-10-locales-20260828.zip"
        with zipfile.ZipFile(package, "w", compression=zipfile.ZIP_STORED) as archive:
            for path in files:
                archive.write(path, str(path.relative_to(folder)))
        with zipfile.ZipFile(package) as archive:
            assert archive.testzip() is None
        report["package"] = str(package)
        report["package_sha256"] = hashlib.sha256(package.read_bytes()).hexdigest()
        report["manifest_sha256"] = hashlib.sha256((folder / "manifest.json").read_bytes()).hexdigest()
        (folder.parent / "package-verification.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
