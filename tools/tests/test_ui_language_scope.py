"""Language expansion must preserve targets and fully translate learning clues."""

import plistlib
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

from tools import release_preflight


ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "ios/Hanco/Hanco/Resources"
UI_LOCALES = {"ja", "en", "es", "de", "fr"}
LEARNING_KEY = re.compile(
    r"(curriculum\..*\.(reading|meaning|item_meaning)|"
    r"practice\.sample_target_[1-3](\.(reading|meaning))?|"
    r"spacing\.passage\..*\.text)"
)


class UILanguageScopeTests(unittest.TestCase):
    def test_ios_ships_all_supported_ui_languages(self):
        with (RESOURCES / "Info.plist").open("rb") as stream:
            self.assertEqual(set(plistlib.load(stream)["CFBundleLocalizations"]), UI_LOCALES)
        project = (ROOT / "ios/Hanco/Hanco.xcodeproj/project.pbxproj").read_text()
        self.assertNotIn("ko.lproj/", project)
        regions = re.search(r"knownRegions = \((.*?)\);", project, re.S).group(1)
        self.assertEqual(set(re.findall(r"\w+", regions)), UI_LOCALES | {"Base"})
        self.assertIn("KoreanLearningContent.strings in Resources", project)

    def test_retired_korean_learning_values_are_preserved_exactly(self):
        original = release_preflight.parse_strings(RESOURCES / "ko.lproj/Localizable.strings")
        expected = {key: value for key, value in original.items() if LEARNING_KEY.fullmatch(key)}
        preserved = release_preflight.parse_strings(RESOURCES / "KoreanLearningContent.strings")
        self.assertTrue(expected)
        self.assertEqual(preserved, expected)
        self.assertNotIn("settings.navigation_title", preserved)
        self.assertNotIn("app.name", preserved)

    def test_all_ui_resources_match_keys_arguments_and_preserve_korean_targets(self):
        tokens = re.compile(r"%(?:\d+\$)?[-+0 #]*(?:\d+)?(?:\.\d+)?(?:ll|l)?[@difus%]")
        def compare(english, spanish):
            self.assertEqual(set(english), set(spanish))
            for key, value in english.items():
                self.assertTrue(spanish[key].strip(), key)
                self.assertEqual(tokens.findall(value), tokens.findall(spanish[key]), key)
        english = release_preflight.parse_strings(RESOURCES / "en.lproj/Localizable.strings")
        for locale in ("es", "de", "fr"):
            candidate = release_preflight.parse_strings(RESOURCES / f"{locale}.lproj/Localizable.strings")
            compare(english, candidate)
            for key, value in english.items():
                if LEARNING_KEY.fullmatch(key):
                    if key.endswith((".meaning", ".item_meaning")):
                        self.assertTrue(candidate[key].strip(), f"{locale}:{key}")
                    else:
                        self.assertEqual(value, candidate[key], f"{locale}:{key}")
            self.assertEqual(candidate["curriculum.chapter_1_basic_consonants.item_meaning"],
                             {"es": "Consonante básica", "de": "Grundkonsonant", "fr": "Consonne de base"}[locale])
            for path in (ROOT / "android").glob("**/src/main/res/values/strings.xml"):
                translated = path.parent.with_name(f"values-{locale}") / "strings.xml"
                compare(
                    {s.attrib["name"]: s.text for s in ET.parse(path).getroot() if s.tag == "string"},
                    {s.attrib["name"]: s.text for s in ET.parse(translated).getroot() if s.tag == "string"},
                )
                originals = ET.parse(path).getroot().findall("string-array")
                translations = ET.parse(translated).getroot()
                for original in originals:
                    candidate_array = translations.find(f"string-array[@name='{original.attrib['name']}']")
                    self.assertIsNotNone(candidate_array)
                    self.assertEqual(len(original), len(candidate_array))
                    self.assertTrue(all(item.text and item.text.strip() for item in candidate_array))
                    for source, target in zip(original, candidate_array):
                        self.assertEqual(tokens.findall(source.text), tokens.findall(target.text))

    def test_shared_korean_content_schema_remains_supported(self):
        import json
        schema = json.loads((ROOT / "shared/schema/deck.schema.json").read_text())
        self.assertIn("ko", schema["$defs"]["deckLocalizations"]["properties"])
        self.assertIn("ko", schema["$defs"]["itemLocalizations"]["properties"])

    def test_learning_item_labels_are_not_mascot_accessories(self):
        for code, expected in {"es": "Elementos", "de": "Einträge", "fr": "Éléments"}.items():
            values = release_preflight.parse_strings(RESOURCES / f"{code}.lproj/Localizable.strings")
            self.assertEqual(values["deck.detail.items"], expected)
            self.assertEqual(values["piyodeck.import.items"], expected)
            self.assertNotEqual(values["deck.detail.items"], values["closet.prop_section"])

    def test_plural_resources_preserve_printf_arguments_and_match_android(self):
        reference = None
        for code in sorted(UI_LOCALES):
            with (RESOURCES / f"{code}.lproj/Localizable.stringsdict").open("rb") as stream:
                plural = plistlib.load(stream)
            if reference is None:
                reference = set(plural)
            self.assertEqual(set(plural), reference)
            for key, entry in plural.items():
                rule = entry["count"]
                self.assertEqual(entry["NSStringLocalizedFormatKey"], "%#@count@")
                for quantity in ("other",) if code == "ja" else ("one", "other"):
                    self.assertEqual(re.findall(r"%[a-z@]", rule[quantity]), ["%d"], key)
            for module, key, expected in (
                ("discover", "deck_item_count", ["%1$d"]),
                ("retention", "review_count", ["%1$d"]),
                ("retention", "retention_week_progress", ["%1$d", "%2$d"]),
            ):
                folder = "values" if code == "en" else f"values-{code}"
                root = ET.parse(ROOT / f"android/feature/{module}/src/main/res/{folder}/strings.xml").getroot()
                quantity = root.find(f"plurals[@name='{key}']")
                self.assertIsNotNone(quantity)
                for item in quantity:
                    self.assertEqual(re.findall(r"%\d+\$[a-z]", item.text), expected)
        with (RESOURCES / "fr.lproj/Localizable.stringsdict").open("rb") as stream:
            self.assertEqual(plistlib.load(stream)["deck.items.format"]["count"]["one"], "%d élément")

    def test_active_visible_copy_uses_current_brand(self):
        for code in UI_LOCALES:
            values = release_preflight.parse_strings(RESOURCES / f"{code}.lproj/Localizable.strings")
            self.assertNotIn("PIYOKEY", " ".join(values.values()))
            folder = "values" if code == "en" else f"values-{code}"
            for path in (ROOT / "android").glob(f"**/src/main/res/{folder}/strings.xml"):
                self.assertNotIn("PIYOKEY", " ".join(ET.parse(path).getroot().itertext()), str(path))

    def test_android_filters_legacy_ui_without_deleting_learning_sources(self):
        config = ET.parse(ROOT / "android/app/src/main/res/xml/locales_config.xml")
        locales = {node.attrib["{http://schemas.android.com/apk/res/android}name"]
                   for node in config.getroot()}
        self.assertEqual(locales, UI_LOCALES)
        build = (ROOT / "android/app/build.gradle.kts").read_text()
        self.assertIn('localeFilters += listOf("en", "ja", "es", "de", "fr")', build)
        path = ROOT / "android/feature/practice/src/main/res/values"
        english = {s.attrib["name"]: s.text for s in ET.parse(path / "strings.xml").getroot()}
        korean = {s.attrib["name"]: s.text for s in ET.parse(path.with_name("values-ko") / "strings.xml").getroot()}
        for key in ("practice_sample_target_1", "practice_sample_target_2", "practice_sample_target_3"):
            self.assertEqual(english[key], korean[key])


if __name__ == "__main__":
    unittest.main()
