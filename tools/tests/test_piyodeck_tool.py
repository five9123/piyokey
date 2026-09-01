import contextlib
import hashlib
import importlib.util
import io
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "tools/piyodeck_tool.py"
SPEC = importlib.util.spec_from_file_location("piyodeck_tool", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
piyodeck_tool = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = piyodeck_tool
SPEC.loader.exec_module(piyodeck_tool)


class PiyoDeckToolTests(unittest.TestCase):
    fixture = ROOT / "shared/piyodeck/fixtures/valid/basic-deck.json"
    multilingual_fixture = ROOT / "shared/piyodeck/fixtures/valid/multilingual-deck.json"
    package_fixture = ROOT / "shared/piyodeck/fixtures/valid/basic.typedeck"
    schema = ROOT / "shared/schema/deck.schema.json"

    def test_new_content_languages_round_trip_without_losing_legacy_fields(self):
        import copy
        source = json.loads(self.fixture.read_text())
        expanded = json.loads((self.fixture.parent / "localized-deck.json").read_text())
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "expanded.json"
            path.write_text(json.dumps(expanded, ensure_ascii=False))
            packed = Path(directory) / "expanded.typedeck"
            piyodeck_tool.pack(path, packed)
            self.assertEqual(packed.read_bytes(), (self.fixture.parent / "localized.typedeck").read_bytes())
            imported = piyodeck_tool.validate_package(packed, self.schema).deck
            self.assertEqual(imported, expanded)
            for original, item in zip(source["items"], imported["items"]):
                for field in ("id", "ko", "meaning_ja", "reading_ja", "audio"):
                    self.assertEqual(item[field], original[field])
        for language in ("es", "de", "fr"):
            incomplete = copy.deepcopy(expanded)
            del incomplete["items"][0]["localizations"][language]
            self.assertTrue(any(f"localizations.{language}" in issue
                                for issue in piyodeck_tool.deck_semantic_issues(incomplete)))

    def test_pack_requires_typedeck_extension(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "legacy.piyodeck"

            with self.assertRaisesRegex(
                piyodeck_tool.PiyoDeckToolError,
                r"must use the \.typedeck extension",
            ):
                piyodeck_tool.pack(self.fixture, output)

            self.assertFalse(output.exists())

    def test_pack_is_deterministic_and_emits_store_entries_in_v1_order(self):
        with tempfile.TemporaryDirectory() as directory:
            first = Path(directory) / "first.typedeck"
            second = Path(directory) / "second.typedeck"

            package = piyodeck_tool.pack(self.fixture, first)
            piyodeck_tool.pack(self.fixture, second)

            self.assertEqual(first.read_bytes(), second.read_bytes())
            self.assertEqual(self.package_fixture.read_bytes(), first.read_bytes())
            imported = piyodeck_tool.validate_package(self.package_fixture, self.schema)
            self.assertEqual(package.deck, imported.deck)
            self.assertEqual(package.manifest["deck"]["deck_version"], 1)
            self.assertEqual(package.manifest["deck"]["item_count"], 2)
            self.assertNotIn(b"\n", package.entries["manifest.json"])
            self.assertNotIn(b"\r", package.entries["manifest.json"])
            self.assertNotIn(b"\n", package.entries["deck.json"])
            self.assertNotIn(b"\r", package.entries["deck.json"])
            # Shared with the Swift package test to pin cross-writer bytes.
            self.assertEqual(len(package.entries["deck.json"]), 580)
            self.assertEqual(
                hashlib.sha256(package.entries["deck.json"]).hexdigest(),
                "7c5b6496007b98ef4ead02b9da6a2dbc560a351e1f214d0df49633ac6c42d90b",
            )
            self.assertEqual(len(package.entries["manifest.json"]), 311)
            self.assertEqual(
                hashlib.sha256(package.entries["manifest.json"]).hexdigest(),
                "fe7fc1ed3af0728582698bd3445d2af2eb68996c500322b1234f16f79f4ff20a",
            )
            self.assertEqual(len(first.read_bytes()), 1109)
            self.assertEqual(
                hashlib.sha256(first.read_bytes()).hexdigest(),
                "025efa7a0584509fd892221a01c4b3cdf828472c3ffeb13a7eec420102061c31",
            )
            self.assertEqual(
                package.manifest["deck"]["sha256"],
                hashlib.sha256(package.entries["deck.json"]).hexdigest(),
            )
            with zipfile.ZipFile(first) as archive:
                self.assertEqual(archive.namelist(), ["manifest.json", "deck.json"])
                self.assertTrue(
                    all(info.compress_type == zipfile.ZIP_STORED for info in archive.infolist())
                )
                self.assertTrue(
                    all(info.date_time == (1980, 1, 1, 0, 0, 0) for info in archive.infolist())
                )

    def test_shared_binary_goldens_are_portable_and_fail_closed(self):
        canonical = piyodeck_tool.validate_package(self.package_fixture, self.schema)
        fixture_root = ROOT / "shared/piyodeck/fixtures"
        manifest = json.loads((fixture_root / "cases.json").read_text(encoding="utf-8"))
        self.assertEqual(2, manifest["schema_version"])
        self.assertGreaterEqual(len(manifest["cases"]), 20)

        for case in manifest["cases"]:
            path = fixture_root / case["path"]
            data = path.read_bytes()
            with self.subTest(case=case["id"]):
                self.assertEqual(case["size_bytes"], len(data))
                self.assertEqual(case["sha256"], hashlib.sha256(data).hexdigest())
                if case["valid"]:
                    imported = piyodeck_tool.validate_package(path, self.schema)
                    if case["id"] == "multilingual-ar-zh-hant":
                        self.assertEqual("ar", imported.deck["default_locale"])
                    else:
                        self.assertEqual(canonical.deck, imported.deck)
                    continue

                with self.assertRaises(piyodeck_tool.PiyoDeckToolError) as caught:
                    piyodeck_tool.validate_package(path, self.schema)
                self.assertEqual(case["expectation"], self.error_family(caught.exception))

    def error_family(self, error: Exception) -> str:
        message = str(error)
        if isinstance(error, piyodeck_tool.DuplicateJSONKeyError):
            return "invalid_json"
        if "SHA-256 does not match" in message:
            return "sha256_mismatch"
        if "unsupported .typedeck format_version" in message or "unsupported deck_schema_version" in message:
            return "unsupported_version"
        if "unsupported ZIP" in message:
            return "unsupported_archive_feature"
        if "unsafe ZIP entry path" in message:
            return "unsafe_entry_path"
        if "ZIP CRC-32 check failed" in message:
            return "crc_mismatch"
        if "malformed ZIP" in message:
            return "malformed_archive"
        if any(marker in message for marker in ("deck schema", "DeckKit semantics")):
            return "invalid_content"
        if any(
            marker in message
            for marker in (
                "invalid JSON",
                "invalid UTF-8",
                "UTF-8 BOM",
                "without a BOM",
                "invalid Unicode",
            )
        ):
            return "invalid_json"
        self.fail(f"unclassified PiyoDeck error: {error}")
        raise AssertionError("unreachable")

    def test_v2_multilingual_pack_round_trip_and_unknown_locale_preservation(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "multilingual.typedeck"
            package = piyodeck_tool.pack(self.multilingual_fixture, output)
            self.assertEqual(2, package.manifest["deck_schema_version"])
            self.assertEqual({"ar", "zh-Hant"}, set(package.deck["localizations"]))
            self.assertEqual(
                (ROOT / "shared/piyodeck/fixtures/valid/multilingual.typedeck").read_bytes(),
                output.read_bytes(),
            )

            unknown = json.loads(self.multilingual_fixture.read_text(encoding="utf-8"))
            unknown["localizations"]["sl-rozaj-biske"] = unknown["localizations"]["zh-Hant"]
            for item in unknown["items"]:
                item["localizations"]["sl-rozaj-biske"] = item["localizations"]["zh-Hant"]
            source = Path(directory) / "unknown.json"
            source.write_text(json.dumps(unknown, ensure_ascii=False), encoding="utf-8")
            unknown_output = Path(directory) / "unknown.typedeck"
            preserved = piyodeck_tool.pack(source, unknown_output)
            self.assertIn("sl-rozaj-biske", preserved.deck["localizations"])
            self.assertEqual(
                preserved.deck,
                piyodeck_tool.validate_package(unknown_output, self.schema).deck,
            )

    def test_language_tag_validation_rejects_malformed_and_noncanonical(self):
        for value in ("ar", "fr-CA", "zh-Hant", "sl-rozaj-biske", "x-private"):
            with self.subTest(value=value):
                self.assertEqual(value, piyodeck_tool.canonical_language_tag(value))
        for value in ("fr_CA", "fr-ca", "EN", "a", "en--US", "en-u"):
            with self.subTest(value=value):
                canonical = piyodeck_tool.canonical_language_tag(value)
                self.assertTrue(canonical is None or canonical != value)

    def test_display_fallback_keeps_legacy_base_japanese_only(self):
        deck = json.loads(self.multilingual_fixture.read_text(encoding="utf-8"))
        deck["localizations"]["fr"] = {
            "name": "Nom français",
            "author_nickname": "Auteur",
            "tags": ["quotidien"],
        }
        deck["localizations"]["en"] = {
            "name": "English name",
            "author_nickname": "Author",
            "tags": ["daily"],
        }
        item = deck["items"][0]
        item["localizations"]["fr"] = {"meaning": "bonjour", "reading": "fr-reading"}
        item["localizations"]["en"] = {"meaning": "hello", "reading": "en-reading"}
        self.assertEqual("Nom français", piyodeck_tool.localized_deck_value(deck, "fr-CA", "name"))
        self.assertEqual("fr-reading", piyodeck_tool.localized_item_value(deck, item, "fr-CA", "reading"))
        self.assertEqual("كلمات كورية", piyodeck_tool.localized_deck_value(deck, "de-DE", "name"))
        for language in ("ja", "ja-JP"):
            self.assertEqual(deck["name"], piyodeck_tool.localized_deck_value(deck, language, "name"))
            self.assertEqual(
                deck["author"]["nickname"],
                piyodeck_tool.localized_deck_value(deck, language, "author_nickname"),
            )
            self.assertEqual(deck["tags"], piyodeck_tool.localized_deck_value(deck, language, "tags"))
            self.assertEqual(
                item["meaning_ja"],
                piyodeck_tool.localized_item_value(deck, item, language, "meaning"),
            )
            self.assertEqual(
                item["reading_ja"],
                piyodeck_tool.localized_item_value(deck, item, language, "reading"),
            )

        del deck["default_locale"]
        deck["localizations"] = {}
        item["localizations"] = {}
        for language in ("en", "es-MX", "de-AT", "fr-CA", "zh-Hant", "ko"):
            self.assertIsNone(piyodeck_tool.localized_deck_value(deck, language, "name"))
            self.assertIsNone(
                piyodeck_tool.localized_item_value(deck, item, language, "meaning")
            )

    def test_inspect_and_validate_commands_report_package_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "basic.typedeck"
            piyodeck_tool.pack(self.fixture, output)

            inspect_stdout = io.StringIO()
            with contextlib.redirect_stdout(inspect_stdout):
                self.assertEqual(piyodeck_tool.main(["inspect", str(output)]), 0)
            summary = json.loads(inspect_stdout.getvalue())
            self.assertEqual(summary["format_version"], 1)
            self.assertEqual(summary["deck"]["deck_version"], 1)
            self.assertEqual(summary["deck"]["item_count"], 2)

            validate_stdout = io.StringIO()
            with contextlib.redirect_stdout(validate_stdout):
                self.assertEqual(
                    piyodeck_tool.main(
                        ["validate", str(output), "--deck-schema", str(self.schema)]
                    ),
                    0,
                )
            self.assertIn("valid:", validate_stdout.getvalue())
            self.assertIn(" v1, 2 items", validate_stdout.getvalue())

    def test_pack_rejects_each_reviewable_invalid_user_deck_fixture(self):
        invalid_directory = ROOT / "shared/piyodeck/fixtures/invalid"
        for name in ("audio-deck.json", "official-deck.json", "unknown-field-deck.json"):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                output = Path(directory) / "invalid.typedeck"
                with self.assertRaises(piyodeck_tool.PiyoDeckToolError):
                    piyodeck_tool.pack(invalid_directory / name, output)
                self.assertFalse(output.exists())

    def test_validate_rejects_crc_valid_package_with_wrong_deck_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "basic.typedeck"
            package = piyodeck_tool.pack(self.fixture, output)
            manifest = json.loads(json.dumps(package.manifest))
            manifest["deck"]["sha256"] = "0" * 64
            tampered = piyodeck_tool.build_deterministic_zip(
                (
                    ("manifest.json", piyodeck_tool.canonical_json(manifest)),
                    ("deck.json", package.entries["deck.json"]),
                )
            )

            with self.assertRaisesRegex(
                piyodeck_tool.PiyoDeckToolError, "SHA-256 does not match"
            ):
                piyodeck_tool.validate_package_data(
                    tampered, piyodeck_tool._load_schema(self.schema)
                )

    def test_validate_rejects_non_store_zip_and_hidden_preamble(self):
        with tempfile.TemporaryDirectory() as directory:
            valid_path = Path(directory) / "valid.typedeck"
            package = piyodeck_tool.pack(self.fixture, valid_path)

            with self.assertRaises(piyodeck_tool.PiyoDeckToolError):
                piyodeck_tool.validate_package_data(
                    b"hidden" + valid_path.read_bytes(),
                    piyodeck_tool._load_schema(self.schema),
                )

            compressed_path = Path(directory) / "compressed.typedeck"
            with zipfile.ZipFile(
                compressed_path, "w", compression=zipfile.ZIP_DEFLATED
            ) as archive:
                archive.writestr("manifest.json", package.entries["manifest.json"])
                archive.writestr("deck.json", package.entries["deck.json"])
            with self.assertRaisesRegex(
                piyodeck_tool.PiyoDeckToolError, "compression method"
            ):
                piyodeck_tool.validate_package(compressed_path, self.schema)

    def test_pack_rejects_duplicate_json_object_keys(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "duplicate.json"
            original = self.fixture.read_text(encoding="utf-8")
            source.write_text(
                '{\n  "deck_id": "user_ffffffffffffffffffffffffffffffff",'
                + original[1:],
                encoding="utf-8",
            )
            with self.assertRaisesRegex(
                piyodeck_tool.DuplicateJSONKeyError, "deck_id"
            ):
                piyodeck_tool.pack(source, Path(directory) / "duplicate.typedeck")

    def test_pack_rejects_noncanonical_timestamp_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "offset-time.json"
            deck = json.loads(self.fixture.read_text(encoding="utf-8"))
            deck["created_at"] = "2026-08-01T09:00:00+09:00"
            source.write_text(json.dumps(deck, ensure_ascii=False), encoding="utf-8")

            with self.assertRaisesRegex(
                piyodeck_tool.PiyoDeckToolError, "created_at"
            ):
                piyodeck_tool.pack(source, Path(directory) / "offset-time.typedeck")

    def test_strict_json_rejects_unpaired_surrogate_escapes(self):
        invalid_values = (
            br'{"name":"\ud800"}',
            br'{"name":"\udc00"}',
            br'{"name":"\ud800\u0041"}',
        )
        for value in invalid_values:
            with self.subTest(value=value), self.assertRaisesRegex(
                piyodeck_tool.PiyoDeckToolError, "invalid Unicode"
            ):
                piyodeck_tool.decode_strict_json(value, "deck.json")

        valid = piyodeck_tool.decode_strict_json(
            br'{"name":"\ud835\udfd9"}', "deck.json"
        )
        self.assertEqual("𝟙", valid["name"])


if __name__ == "__main__":
    unittest.main()
