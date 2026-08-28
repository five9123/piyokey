"""UI locale removal must never remove Korean learning content."""

import plistlib
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

from tools import release_preflight


ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "ios/Hanco/Hanco/Resources"
LEARNING_KEY = re.compile(
    r"(curriculum\..*\.(reading|meaning|item_meaning)|"
    r"practice\.sample_target_[1-3](\.(reading|meaning))?|"
    r"spacing\.passage\..*\.text)"
)


class UILanguageScopeTests(unittest.TestCase):
    def test_ios_ships_only_japanese_and_english_ui(self):
        with (RESOURCES / "Info.plist").open("rb") as stream:
            self.assertEqual(set(plistlib.load(stream)["CFBundleLocalizations"]), {"ja", "en"})
        project = (ROOT / "ios/Hanco/Hanco.xcodeproj/project.pbxproj").read_text()
        self.assertNotIn("ko.lproj/", project)
        regions = re.search(r"knownRegions = \((.*?)\);", project, re.S).group(1)
        self.assertEqual(set(re.findall(r"\w+", regions)), {"ja", "en", "Base"})
        self.assertIn("KoreanLearningContent.strings in Resources", project)

    def test_retired_korean_learning_values_are_preserved_exactly(self):
        original = release_preflight.parse_strings(RESOURCES / "ko.lproj/Localizable.strings")
        expected = {key: value for key, value in original.items() if LEARNING_KEY.fullmatch(key)}
        preserved = release_preflight.parse_strings(RESOURCES / "KoreanLearningContent.strings")
        self.assertTrue(expected)
        self.assertEqual(preserved, expected)
        self.assertNotIn("settings.navigation_title", preserved)
        self.assertNotIn("app.name", preserved)

    def test_shared_korean_content_schema_remains_supported(self):
        import json
        schema = json.loads((ROOT / "shared/schema/deck.schema.json").read_text())
        self.assertIn("ko", schema["$defs"]["deckLocalizations"]["properties"])
        self.assertIn("ko", schema["$defs"]["itemLocalizations"]["properties"])

    def test_android_filters_legacy_ui_without_deleting_learning_sources(self):
        config = ET.parse(ROOT / "android/app/src/main/res/xml/locales_config.xml")
        locales = {node.attrib["{http://schemas.android.com/apk/res/android}name"]
                   for node in config.getroot()}
        self.assertEqual(locales, {"ja", "en"})
        build = (ROOT / "android/app/build.gradle.kts").read_text()
        self.assertIn('localeFilters += listOf("en", "ja")', build)
        path = ROOT / "android/feature/practice/src/main/res/values"
        english = {s.attrib["name"]: s.text for s in ET.parse(path / "strings.xml").getroot()}
        korean = {s.attrib["name"]: s.text for s in ET.parse(path.with_name("values-ko") / "strings.xml").getroot()}
        for key in ("practice_sample_target_1", "practice_sample_target_2", "practice_sample_target_3"):
            self.assertEqual(english[key], korean[key])


if __name__ == "__main__":
    unittest.main()
