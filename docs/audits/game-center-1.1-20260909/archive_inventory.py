#!/usr/bin/env python3
"""Read the actual archived app's ranked decks, IDs and localized CTA resources."""
import argparse
import hashlib
import json
from pathlib import Path
import plistlib


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("app", type=Path, help="Path to Hanco.app inside the shipping xcarchive")
    args = parser.parse_args()
    app = args.app
    info = plistlib.loads((app / "Info.plist").read_bytes())
    assert (info["CFBundleShortVersionString"], info["CFBundleVersion"]) == ("1.1", "24")
    rows = []
    for game, version in [("flow", 4), ("acid_rain", 3), ("choseong", 3), ("dictation", 3), ("word_match", 4)]:
        for level in ["beginner", "intermediate", "advanced"]:
            deck_id = f"{game}_topik_{level}"
            path = app / "decks" / game / f"{deck_id}_v3.json"
            data = json.loads(path.read_text())
            assert data["deck_id"] == deck_id and data["version"] == 3 and len(data["items"]) == 100
            rows.append({"game": game, "level": level, "deck_id": deck_id, "deck_version": 3,
                         "item_count": 100, "board_id": f"piyokey.v{version}.{game}.{level}",
                         "deck_sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    expected = {row["board_id"] for row in rows} | {"piyokey.v4.cup.weekly.flow"}
    assert expected == set(info["PiyokeyGameCenterAvailableLeaderboardIDs"])
    assert expected == set(info["PiyokeyGameCenterIntendedLeaderboardIDs"])
    localized = {}
    for language in ["ja", "en", "es", "de", "fr"]:
        strings = plistlib.loads((app / f"{language}.lproj" / "Localizable.strings").read_bytes())
        localized[language] = {key: strings[key] for key in [
            "game_center.result_action", "game_center.result_rank_format",
            "input_mode.builtin", "input_mode.builtin_korean_10key",
        ]}
        assert all(localized[language].values())
    print(json.dumps({"version": "1.1", "build": "24", "bundle_id": info["CFBundleIdentifier"],
                      "preset_contracts": rows, "available_ids": sorted(expected),
                      "intended_ids": sorted(expected), "localized_result_strings": localized,
                      "status": "all archived contracts matched; no device language-switch test performed"},
                     ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
