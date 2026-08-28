#!/usr/bin/env python3
"""Build reviewed 13-inch iPad screenshots; never uploads or alters the app."""
from __future__ import annotations

import argparse
import hashlib
import html
import json
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")


def select_attachment(entries, scene, language):
    prefix = f"appstore-current-{scene}-{language}_"
    matches = [a for a in entries if a["suggestedHumanReadableName"].startswith(prefix)]
    if len(matches) != 1 or matches[0].get("isAssociatedWithFailure"):
        raise ValueError(f"Expected one passing capture: {prefix}")
    return matches[0]["exportedFileName"]


def build(capture: Path, output: Path, renderer: Path):
    from PIL import Image, ImageDraw

    copy = json.loads((ROOT / "release/store-assets/localizations.json").read_text())
    output.mkdir(parents=True, exist_ok=False)
    # The renderer resolves source/output paths against the repository.
    output.relative_to(ROOT)
    source_manifest = []
    for language in copy["app_ui_languages"]:
        folder = capture / language
        if json.loads((folder / "timing.json").read_text())["test_exit_code"] != 0:
            raise ValueError(f"Capture failed: {language}")
        entries = [a for t in json.loads((folder / "attachments/manifest.json").read_text()) for a in t["attachments"]]
        destination = output / "sources" / language
        destination.mkdir(parents=True)
        for scene in copy["screenshot_scenes"] + ["keyboard-landscape"]:
            original = folder / "attachments" / select_attachment(entries, scene, language)
            target = destination / f"{scene}.png"
            shutil.copy2(original, target)
            with Image.open(target) as image:
                expected = (2752, 2064) if scene == "keyboard-landscape" else (2064, 2752)
                if image.size != expected:
                    raise ValueError(f"Unexpected capture dimensions: {target}: {image.size}")
            source_manifest.append({"file": str(target.relative_to(output)), "sha256": sha(target)})

    outputs = []
    sections = []
    for locale in copy["locales"]:
        code, language = locale["locale"], locale["ui"]
        folder = output / code
        folder.mkdir()
        specs = [{"output": f"{i + 1:02d}-{scene}.png", "source": f"{scene}.png",
                  "title": words[0], "subtitle": words[1]}
                 for i, (scene, words) in enumerate(zip(copy["screenshot_scenes"], locale["screenshots"], strict=True))]
        for family, subfolder, shots in [
            ("ipad", "screenshots", specs),
            ("ipad-landscape", "alternate", [{"output": "keyboard-landscape.png",
                "source": "keyboard-landscape.png", "title": locale["screenshots"][0][0],
                "subtitle": locale["screenshots"][0][1]}]),
        ]:
            batch = {"sourceDirectory": str((output / "sources" / language).relative_to(ROOT)),
                     "outputDirectory": str((folder / subfolder).relative_to(ROOT)),
                     "deviceFamily": family, "brand": locale["brand"], "disclosure": "", "specs": shots}
            batch_path = folder / f"{subfolder}-batch.json"
            save(batch_path, batch)
            subprocess.run([str(renderer), str(batch_path)], cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
            for shot in shots:
                path = folder / subfolder / shot["output"]
                with Image.open(path) as image:
                    expected = (2064, 2752) if family == "ipad" else (2752, 2064)
                    if image.size != expected or image.mode != "RGB":
                        raise ValueError(f"Invalid store PNG: {path}: {image.size}, {image.mode}")
                    image.verify()
                outputs.append({"file": str(path.relative_to(output)), "sha256": sha(path),
                                "size": expected, "ui": language, "alternate": subfolder == "alternate"})
        contact = Image.new("RGB", (1440, 832), "#faf6f9")
        draw = ImageDraw.Draw(contact)
        for i, shot in enumerate(specs):
            with Image.open(folder / "screenshots" / shot["output"]) as image:
                image.thumbnail((276, 368), Image.Resampling.LANCZOS)
                x, y = (i % 5) * 288 + 6, (i // 5) * 416
                contact.paste(image, (x, y))
                draw.text((x, y + 376), shot["output"], fill="#30253d")
        contact.save(folder / "contact.png")
        pics = "".join(f'<a href="{code}/screenshots/{s["output"]}"><img loading="lazy" src="{code}/screenshots/{s["output"]}" alt="{html.escape(s["title"])}"></a>' for s in specs)
        sections.append(f'<section id="{code}"><h2>{html.escape(locale["market"])} · {code}</h2><p>실제 UI: {language} · 13인치 PNG 10장 · <a href="{code}/alternate/keyboard-landscape.png">가로 대체 이미지</a></p><div class="grid">{pics}</div></section>')
        print(f"{code}: 10 portrait + 1 landscape, dimensions/RGB/decode verified", flush=True)
    save(output / "manifest.json", {"issue": 79, "uploaded": False, "store_slot": "iPad 13-inch",
        "source_commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "source_diff_sha256": hashlib.sha256(subprocess.check_output(["git", "diff", "--", "ios"], cwd=ROOT)).hexdigest(),
        "note": "Local iPad improvements; not the uploaded TestFlight build 7. Final RC and native-language review remain required.",
        "primary_count": 100, "alternate_count": 10, "sources": source_manifest, "images": outputs})
    output.joinpath("index.html").write_text('''<!doctype html><html lang="ko"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>typee · iPad App Store</title><style>body{margin:0;background:#faf6f9;color:#30253d;font:16px/1.6 system-ui}header,main{max-width:1560px;margin:auto;padding:28px}h1{font-size:40px}.grid{display:grid;grid-template-columns:repeat(5,1fr);gap:18px}img{width:100%;border-radius:12px;box-shadow:0 4px 24px #30253d15}section{padding:24px 0 42px;border-top:1px solid #e4d8df}a{color:#b62967}.note{padding:20px;background:white;border-radius:16px}@media(max-width:850px){.grid{grid-template-columns:repeat(2,1fr)}}</style><header><h1>typee / ピヨキー · iPad</h1><div class="note">수정한 실제 iPad 앱 화면으로 제작한 검토용 세트입니다. 스토어 미업로드, 기존 TestFlight 빌드 7과 다릅니다. 언어별 10장 안에서 가로 대체본을 선택할 수 있습니다. 최종 RC 일치·현지어 검수·기기 QA는 별도입니다.</div></header><main>''' + "".join(sections) + "</main></html>")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--capture", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--renderer", required=True, type=Path)
    args = parser.parse_args()
    build(args.capture.resolve(), args.output.resolve(), args.renderer.resolve())
