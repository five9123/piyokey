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
            self.assertEqual(loc["ui"], {"ja": "ja", "ko": "ko"}.get(loc["locale"], "en"))
            self.assertEqual(loc["brand"], "ピヨキー" if loc["locale"] == "ja" else "typee")
            self.assertEqual(loc["disclosure"], "")
            self.assertEqual(loc["video_disclosure"], "")


if __name__ == "__main__":
    unittest.main()
