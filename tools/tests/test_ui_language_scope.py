"""Language expansion must preserve targets and fully translate learning clues."""

import plistlib
import re
import unittest
from pathlib import Path

from tools import release_preflight


ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "ios/Hanco/Hanco/Resources"
UI_LOCALES = {"ja", "en", "es", "de", "fr"}
PRIVACY_MODAL_KEYS = {
    "privacy_consent.navigation_title",
    "privacy_consent.title",
    "privacy_consent.introduction",
    "privacy_consent.usage_title",
    "privacy_consent.usage_detail",
    "privacy_consent.error_title",
    "privacy_consent.error_detail",
    "privacy_consent.excluded_data",
    "privacy_consent.settings_note",
    "privacy_consent.participate_and_continue",
    "privacy_consent.continue_without_sharing",
}
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

    def test_privacy_modal_copy_is_synced_and_provider_neutral_in_all_six_sources(self):
        for locale in UI_LOCALES | {"ko"}:
            values = release_preflight.parse_strings(
                RESOURCES / f"{locale}.lproj/Localizable.strings"
            )
            self.assertTrue(PRIVACY_MODAL_KEYS.issubset(values), locale)
            modal_copy = " ".join(values[key] for key in PRIVACY_MODAL_KEYS)
            self.assertTrue(all(values[key].strip() for key in PRIVACY_MODAL_KEYS), locale)
            for provider_term in ("posthog", "firebase", "crashlytics", "cloud"):
                self.assertNotIn(provider_term, modal_copy.lower(), locale)

        korean = release_preflight.parse_strings(
            RESOURCES / "ko.lproj/Localizable.strings"
        )
        self.assertEqual(korean["privacy_consent.title"], "피요키 개선에 참여할까요?")
        self.assertEqual(
            korean["privacy_consent.participate_and_continue"], "참여하고 계속"
        )
        self.assertEqual(
            korean["privacy_consent.continue_without_sharing"], "공유하지 않고 계속"
        )

    def test_shared_korean_content_schema_remains_supported(self):
        import json
        schema = json.loads((ROOT / "shared/schema/deck.schema.json").read_text())
        locale_pattern = re.compile(schema["$defs"]["localeTag"]["pattern"])
        self.assertIsNotNone(locale_pattern.fullmatch("ko"))
        for definition, value in (
            ("deckLocalizations", "deckLocalization"),
            ("itemLocalizations", "itemLocalization"),
        ):
            localizations = schema["$defs"][definition]
            self.assertEqual(localizations["propertyNames"], {"$ref": "#/$defs/localeTag"})
            self.assertEqual(localizations["additionalProperties"], {"$ref": f"#/$defs/{value}"})

    def test_learning_item_labels_are_not_mascot_accessories(self):
        for code, expected in {"es": "Elementos", "de": "Einträge", "fr": "Éléments"}.items():
            values = release_preflight.parse_strings(RESOURCES / f"{code}.lproj/Localizable.strings")
            self.assertEqual(values["deck.detail.items"], expected)
            self.assertEqual(values["piyodeck.import.items"], expected)
            self.assertNotEqual(values["deck.detail.items"], values["closet.prop_section"])

    def test_plural_resources_preserve_printf_arguments(self):
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
        with (RESOURCES / "fr.lproj/Localizable.stringsdict").open("rb") as stream:
            self.assertEqual(plistlib.load(stream)["deck.items.format"]["count"]["one"], "%d élément")

    def test_active_visible_copy_uses_current_brand(self):
        for code in UI_LOCALES:
            values = release_preflight.parse_strings(RESOURCES / f"{code}.lproj/Localizable.strings")
            self.assertNotIn("PIYOKEY", " ".join(values.values()))


if __name__ == "__main__":
    unittest.main()
