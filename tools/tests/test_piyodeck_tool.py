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
    schema = ROOT / "shared/schema/deck.schema.json"

    def test_pack_is_deterministic_and_emits_store_entries_in_v1_order(self):
        with tempfile.TemporaryDirectory() as directory:
            first = Path(directory) / "first.piyodeck"
            second = Path(directory) / "second.piyodeck"

            package = piyodeck_tool.pack(self.fixture, first)
            piyodeck_tool.pack(self.fixture, second)

            self.assertEqual(first.read_bytes(), second.read_bytes())
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

    def test_inspect_and_validate_commands_report_package_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "basic.piyodeck"
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
                output = Path(directory) / "invalid.piyodeck"
                with self.assertRaises(piyodeck_tool.PiyoDeckToolError):
                    piyodeck_tool.pack(invalid_directory / name, output)
                self.assertFalse(output.exists())

    def test_validate_rejects_crc_valid_package_with_wrong_deck_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "basic.piyodeck"
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
            valid_path = Path(directory) / "valid.piyodeck"
            package = piyodeck_tool.pack(self.fixture, valid_path)

            with self.assertRaises(piyodeck_tool.PiyoDeckToolError):
                piyodeck_tool.validate_package_data(
                    b"hidden" + valid_path.read_bytes(),
                    piyodeck_tool._load_schema(self.schema),
                )

            compressed_path = Path(directory) / "compressed.piyodeck"
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
                piyodeck_tool.pack(source, Path(directory) / "duplicate.piyodeck")


if __name__ == "__main__":
    unittest.main()
