#!/usr/bin/env python3
"""Render the ten-market App Store asset pack from fresh XCTest captures."""
from __future__ import annotations

import argparse
import hashlib
import html
import json
import shutil
import subprocess
from datetime import datetime, timezone
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
COPY_PATH = ROOT / "release/store-assets/localizations.json"


def load(path: Path):
    return json.loads(path.read_text())


def save(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def relative(path: Path) -> str:
    return str(path.resolve().relative_to(ROOT))


def run(*args: str) -> str:
    process = subprocess.run(args, cwd=ROOT, capture_output=True, text=True, timeout=240)
    if process.returncode:
        raise RuntimeError(f"{' '.join(args)}\n{process.stdout}\n{process.stderr}")
    return process.stdout


def contact_sheet(images: list[Path], output: Path) -> None:
    width, height = 264, 574
    canvas = Image.new("RGB", (width * 5, (height + 28) * 2), "#f8f5f8")
    draw = ImageDraw.Draw(canvas)
    for index, path in enumerate(images):
        with Image.open(path) as image:
            if image.size != (1320, 2868) or image.mode != "RGB":
                raise ValueError(f"Invalid screenshot: {path}: {image.size} {image.mode}")
            image.thumbnail((width, height), Image.Resampling.LANCZOS)
            x, y = (index % 5) * width, (index // 5) * (height + 28)
            canvas.paste(image, (x, y))
            draw.text((x + 10, y + height + 6), path.name[:30], fill="#453954")
    canvas.save(output)


def prepare_sources(capture: Path, output: Path, ui: str, scenes: list[str]) -> dict:
    folder = capture / ui
    timing = load(folder / "timing.json")
    if timing["test_exit_code"] != 0:
        raise ValueError(f"UI capture failed: {ui}")
    entries = [a for t in load(folder / "attachments/manifest.json") for a in t["attachments"]]
    source = output / "sources" / ui
    source.mkdir(parents=True, exist_ok=True)
    for scene in scenes:
        prefix = f"appstore-current-{scene}-{ui}"
        matches = [a for a in entries if a["suggestedHumanReadableName"].startswith(prefix)]
        if len(matches) != 1:
            raise ValueError(f"Expected one {prefix}: {matches}")
        shutil.copy2(folder / "attachments" / matches[0]["exportedFileName"], source / f"{scene}.png")
    return timing


def make_gallery(output: Path, locales: list[dict]) -> None:
    sections = []
    for locale in locales:
        code = locale["locale"]
        shots = sorted((output / code / "screenshots").glob("*.png"))
        pictures = "".join(f'<a href="{code}/screenshots/{p.name}"><img loading="lazy" src="{code}/screenshots/{p.name}" alt="{html.escape(p.stem)}"></a>' for p in shots)
        sections.append(f'''<section id="{code}"><h2>{html.escape(locale['market'])} · {html.escape(locale['language'])} <small>{code}</small></h2>
        <p>실제 앱 UI: {locale['ui']} · 현지화 마케팅 문구 · 원본 PNG 10장 / MP4 1개</p>
        <div class="media"><video controls preload="metadata" poster="{code}/screenshots/{shots[0].name}" src="{code}/preview.mp4"></video><div class="shots">{pictures}</div></div></section>''')
    links = " · ".join(f'<a href="#{x["locale"]}">{html.escape(x["market"])}</a>' for x in locales)
    output.joinpath("index.html").write_text(f'''<!doctype html><html lang="ko"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>typee · App Store 10개 로케일</title>
    <style>*{{box-sizing:border-box}}body{{margin:0;background:#faf6f9;color:#30253d;font-family:system-ui,sans-serif}}header,main{{max-width:1480px;margin:auto;padding:32px}}h1{{font-size:36px;margin-bottom:10px}}p{{color:#6e6079;line-height:1.6}}nav{{line-height:2.2}}a{{color:#b62967}}section{{padding:22px 0 48px;border-top:1px solid #eadfe7;scroll-margin-top:12px}}h2{{font-size:24px}}small{{font-size:14px;color:#81718a}}.media{{display:grid;grid-template-columns:220px 1fr;gap:22px}}video{{width:220px;border-radius:24px;background:#21192d;max-height:490px}}.shots{{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:12px}}img{{display:block;width:100%;border-radius:16px;box-shadow:0 3px 16px #392b4112}}.note{{padding:18px 22px;background:#fff;border-radius:18px;border:1px solid #eadfe7}}@media(max-width:850px){{.media{{grid-template-columns:1fr}}.shots{{grid-template-columns:repeat(2,1fr)}}header,main{{padding:20px}}}}</style>
    <header><p>iOS 1.1 · LOCAL REVIEW PACK</p><h1>typee / ピヨキー<br>10개 로케일 스토어 미디어</h1><div class="note">최신 작업 소스에서 재촬영한 검토용 패키지입니다. 아직 App Store Connect에 업로드하지 않았습니다. 일본어·영어·한국어 외 문구는 마케팅 현지화이며 실제 앱 화면은 영어입니다. 최종 제출 빌드와의 일치 검증 및 현지어 최종 검수가 필요합니다.</div><nav>{links}</nav></header><main>{''.join(sections)}</main></html>''')


def build(capture: Path, output: Path, only: list[str] | None, videos: bool, video_only: bool = False) -> None:
    data = load(COPY_PATH)
    locales = [loc for loc in data["locales"] if not only or loc["locale"] in only]
    output.mkdir(parents=True, exist_ok=True)
    records = []
    for loc in locales:
        code, ui = loc["locale"], loc["ui"]
        print(f"Rendering {code} (real UI: {ui})", flush=True)
        timing = prepare_sources(capture, output, ui, data["screenshot_scenes"])
        folder = output / code
        folder.mkdir(exist_ok=True)
        spec = {
            "sourceDirectory": relative(output / "sources" / ui),
            "outputDirectory": relative(folder / "screenshots"),
            "brand": loc["brand"], "disclosure": loc["disclosure"],
            "specs": [{"output": f"{i+1:02d}-{scene}-{code}.png", "source": f"{scene}.png", "title": copy[0], "subtitle": copy[1]}
                      for i, (scene, copy) in enumerate(zip(data["screenshot_scenes"], loc["screenshots"]))],
        }
        save(folder / "screenshot-spec.json", spec)
        if not video_only:
            run("/tmp/typee-screenshot-renderer", str(folder / "screenshot-spec.json"))
        screenshots = [folder / "screenshots" / s["output"] for s in spec["specs"]]
        if not video_only:
            contact_sheet(screenshots, folder / "screenshot-contact.png")
        clips = []
        for index, (scene, caption, duration) in enumerate(zip(data["preview_scenes"], loc["preview"], [7.25, 2.85, 7.45, 3.80, 3.30, 3.0])):
            detail = loc["video_disclosure"] or None
            if index == 5:
                detail = loc["purchase_disclosure"] + ("\n" + loc["video_disclosure"] if loc["video_disclosure"] else "")
            clips.append({"filename": "capture.mp4", "start": timing["scenes"][scene] + (0.8 if scene in {"practice", "result"} else 0.15),
                          "duration": duration, "caption": caption, "detail": detail, "captionAtBottom": scene == "home"})
        save(folder / "preview-spec.json", clips)
        video = folder / "preview.mp4"
        if videos:
            run("/tmp/typee-preview-composer", str(capture / ui), str(folder / "preview-silent-video.mp4"), str(folder / "preview-spec.json"))
            run("/tmp/typee-preview-audio", str(folder / "preview-silent-video.mp4"), str(video))
            check = run("/tmp/typee-preview-checker", "validate", str(video))
            (folder / "video-validation.txt").write_text(check)
            run("/tmp/typee-preview-checker", "contact-sheet", str(video), str(folder / "preview-contact.png"))
        record = {"locale": code, "real_ui_language": ui, "source_capture": relative(capture / ui),
                  "screenshots": [{"path": relative(p), "sha256": sha(p)} for p in screenshots],
                  "preview": {"path": relative(video), "sha256": sha(video)} if video.exists() else None,
                  "uploaded": False, "visual_review": "pending", "native_language_review": "pending"}
        save(folder / "manifest.json", record)
        records.append(record)
        print(f"Finished {code}: 10 PNGs" + (" + preview MP4" if videos else ""), flush=True)
    save(output / "manifest.json", {"generated_at": datetime.now(timezone.utc).isoformat(), "app_version": data["app_version"],
         "source_head": run("git", "rev-parse", "HEAD").strip(), "source_worktree_dirty": True,
         "source_diff_sha256": hashlib.sha256(run("git", "diff", "--binary", "--", "ios").encode()).hexdigest(),
         "copy_sha256": sha(COPY_PATH), "release_candidate_verified": False, "uploaded": False, "locales": records})
    if len(locales) == 10:
        make_gallery(output, locales)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--capture", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--locales", nargs="+")
    parser.add_argument("--videos", action="store_true")
    parser.add_argument("--video-only", action="store_true")
    args = parser.parse_args()
    build(args.capture.resolve(), args.output.resolve(), args.locales, args.videos or args.video_only, args.video_only)
