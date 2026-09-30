import importlib.util
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("gen_android_strings", ROOT / "tools/gen_android_strings.py")
GENERATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GENERATOR)

APPLE_TERMS = re.compile(r"App Store|Apple Account|compte Apple|cuenta de Apple|iPhone|Magic Keyboard|Game Center")


class AndroidStringsTest(unittest.TestCase):
    def test_committed_android_strings_match_ios_sources(self):
        for path, content in GENERATOR.render_all().items():
            with self.subTest(path=path.relative_to(ROOT)):
                self.assertEqual(content, path.read_text(encoding="utf-8"))

    def test_ios_format_specifiers_become_android_specifiers(self):
        self.assertEqual(GENERATOR.convert_format("%@ scored %d"), ("%1$s scored %2$d", True))
        self.assertEqual(GENERATOR.convert_format("%d%%"), ("%d%%", True))
        self.assertEqual(GENERATOR.convert_format("100%"), ("100%", False))

    def test_android_overrides_replace_apple_specific_copy_in_every_locale(self):
        overrides = GENERATOR.load_overrides()
        self.assertEqual(set(overrides), set(GENERATOR.UI_LOCALES))
        for locale, values in overrides.items():
            for key, value in values.items():
                with self.subTest(locale=locale, key=key):
                    self.assertNotRegex(value, APPLE_TERMS)
                    name = GENERATOR.android_name(key)
                    folder = "values" if locale == GENERATOR.DEFAULT_LOCALE else f"values-{locale}"
                    xml = (GENERATOR.ANDROID_RES / folder / GENERATOR.OUTPUT_NAME).read_text(encoding="utf-8")
                    self.assertIn(f'name="{name}">{GENERATOR.escape_android(value)}<', xml)


if __name__ == "__main__":
    unittest.main()
