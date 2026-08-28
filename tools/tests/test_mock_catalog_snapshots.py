import json
import tempfile
import unittest
from datetime import datetime
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CATALOG_ROOT = ROOT / "shared" / "mock_catalog"


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def instant(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


class MockCatalogSnapshotTests(unittest.TestCase):
    def test_expanded_localizations_cover_catalog_games_and_update_previews(self):
        from tools import gen_mock_catalog, piyodeck_tool
        translations = gen_mock_catalog.load_additional_meanings()
        self.assertEqual(set(translations), {"es", "de", "fr"})
        english = gen_mock_catalog.load_content_localizations()[0]
        for language, meanings in translations.items():
            self.assertEqual(set(meanings), set(english))
            self.assertTrue(all(value.strip() for value in meanings.values()))
        for root in (CATALOG_ROOT, CATALOG_ROOT / "updates"):
            catalog = load_json(root / "catalog.json")
            for entry in catalog["decks"]:
                payload = (root / entry["file_url"]).read_bytes()
                deck = json.loads(payload)
                self.assertEqual(entry["size_bytes"], len(payload))
                for language in translations:
                    self.assertEqual(entry["localizations"][language], deck["localizations"][language])
                    for preview, item in zip(entry["preview_items"], deck["items"]):
                        self.assertEqual(preview["localizations"][language]["meaning"],
                                         item["localizations"][language]["meaning"])
            for path in (root / "decks").rglob("*.json"):
                deck = load_json(path)
                self.assertFalse(piyodeck_tool.deck_semantic_issues(deck), str(path))
                for item in deck["items"]:
                    for language, meanings in translations.items():
                        self.assertEqual(item["localizations"][language]["meaning"],
                                         meanings[(item["ko"], item["meaning_ja"])])
                        self.assertEqual(item["localizations"][language]["reading"],
                                         item["localizations"]["en"]["reading"])

    def test_generation_reproduces_expanded_catalog_and_decks(self):
        from tools import gen_mock_catalog
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            gen_mock_catalog.generate(target, gen_mock_catalog.DEFAULT_SEED)
            for generated in target.rglob("*.json"):
                committed = CATALOG_ROOT / generated.relative_to(target)
                self.assertEqual(generated.read_bytes(), committed.read_bytes(), str(committed))

    def test_base_and_update_snapshots_are_monotonic_and_complete(self) -> None:
        update_root = CATALOG_ROOT / "updates"
        base_catalog = load_json(CATALOG_ROOT / "catalog.json")
        update_catalog = load_json(update_root / "catalog.json")

        self.assertEqual(11, base_catalog["catalog_version"])
        self.assertEqual(12, update_catalog["catalog_version"])
        self.assertGreater(
            instant(update_catalog["generated_at"]),
            instant(base_catalog["generated_at"]),
        )

        base_entries = {entry["deck_id"]: entry for entry in base_catalog["decks"]}
        update_entries = {entry["deck_id"]: entry for entry in update_catalog["decks"]}
        self.assertEqual(26, len(base_entries))
        self.assertEqual(base_entries.keys(), update_entries.keys())

        changed_ids: set[str] = set()
        for deck_id, base_entry in base_entries.items():
            update_entry = update_entries[deck_id]
            base_deck = load_json(CATALOG_ROOT / base_entry["file_url"])
            update_deck = load_json(update_root / update_entry["file_url"])

            self.assertLessEqual(
                instant(base_deck["updated_at"]),
                instant(base_catalog["generated_at"]),
                deck_id,
            )
            self.assertLessEqual(
                instant(base_deck["updated_at"]),
                instant(update_deck["updated_at"]),
                deck_id,
            )
            self.assertLessEqual(
                instant(update_deck["updated_at"]),
                instant(update_catalog["generated_at"]),
                deck_id,
            )
            self.assertGreaterEqual(update_deck["version"], base_deck["version"], deck_id)

            if update_deck != base_deck:
                changed_ids.add(deck_id)

        self.assertEqual({"official_daily_words"}, changed_ids)
        base_daily = load_json(CATALOG_ROOT / base_entries["official_daily_words"]["file_url"])
        update_daily = load_json(
            update_root / update_entries["official_daily_words"]["file_url"]
        )
        self.assertEqual(base_daily["version"] + 1, update_daily["version"])
        self.assertEqual(len(base_daily["items"]) + 1, len(update_daily["items"]))
        self.assertEqual("약속", update_daily["items"][-1]["ko"])


if __name__ == "__main__":
    unittest.main()
