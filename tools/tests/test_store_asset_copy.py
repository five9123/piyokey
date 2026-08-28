import json
import unittest
from pathlib import Path


class StoreAssetCopyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = json.loads((Path(__file__).resolve().parents[2] / "release/store-assets/localizations.json").read_text())

    def test_ten_distinct_store_locales(self):
        self.assertEqual({x["locale"] for x in self.data["locales"]},
                         {"ja", "en-US", "ko", "zh-Hans", "zh-Hant", "de-DE", "fr-FR", "es-ES", "pt-BR", "id"})
        self.assertEqual(len(self.data["locales"]), 10)

    def test_asset_counts_and_copy(self):
        self.assertEqual(len(self.data["screenshot_scenes"]), 10)
        self.assertEqual(len(self.data["preview_scenes"]), 6)
        for loc in self.data["locales"]:
            with self.subTest(locale=loc["locale"]):
                self.assertEqual(len(loc["screenshots"]), 10)
                self.assertEqual(len(loc["preview"]), 6)
                self.assertTrue(all(len(s) == 2 and all(s) for s in loc["screenshots"]))
                self.assertTrue(loc["purchase_disclosure"])
                self.assertNotIn("deck maker", json.dumps(loc).lower())
                self.assertNotIn("課金なし", json.dumps(loc, ensure_ascii=False))

    def test_real_ui_without_language_disclaimer(self):
        for loc in self.data["locales"]:
            self.assertEqual(loc["ui"], {"ja": "ja", "es-ES": "es", "de-DE": "de", "fr-FR": "fr"}.get(loc["locale"], "en"))
            self.assertEqual(loc["brand"], "ピヨキー" if loc["locale"] == "ja" else "typee")
            self.assertEqual(loc["disclosure"], "")
            self.assertEqual(loc["video_disclosure"], "")

    def test_new_locale_store_drafts_are_complete_and_not_published(self):
        root = Path(__file__).resolve().parents[2]
        draft = json.loads((root / "release/language_expansion_store_draft.json").read_text())
        self.assertFalse(draft["uploaded"])
        self.assertTrue(draft["catalog_delivery"]["new_namespace_required"])
        self.assertEqual(set(draft["locales"]), {"es-ES", "de-DE", "fr-FR"})
        for locale, content in draft["locales"].items():
            for field, limit in (("name", 30), ("subtitle", 30), ("keywords", 100),
                                 ("description", 4000), ("play_short_description", 80),
                                 ("pro_name", 30), ("pro_description", 45)):
                self.assertTrue(0 < len(content[field]) <= limit, f"{locale}:{field}")
            self.assertEqual(content["ui_language"], locale[:2])
            self.assertTrue(all(content["support"].values()))


if __name__ == "__main__":
    unittest.main()
