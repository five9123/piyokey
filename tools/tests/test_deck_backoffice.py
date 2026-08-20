import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "tools" / "deck_backoffice" / "server.py"
SPEC = importlib.util.spec_from_file_location("deck_backoffice_server", MODULE_PATH)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def sample_deck(deck_id: str = "official_sample", version: int = 1) -> dict:
    return {
        "deck_id": deck_id,
        "version": version,
        "name": "サンプル韓国語",
        "author": {"id": "official_hanco", "nickname": "ピヨキー 公式"},
        "official": True,
        "type": "word",
        "level": 1,
        "tags": ["日常", "公式"],
        "created_at": "2026-07-20T00:00:00Z",
        "updated_at": "2026-07-20T00:00:00Z",
        "items": [
            {
                "id": "i_001",
                "ko": "안녕",
                "reading_ja": "アンニョン",
                "meaning_ja": "こんにちは",
                "audio": None,
            }
        ],
    }


def add_localizations(deck: dict) -> dict:
    deck["localizations"] = {
        "en": {
            "name": "Sample Korean",
            "author_nickname": "typee Official",
            "tags": ["Everyday", "Official"],
        },
        "ko": {
            "name": "한국어 샘플",
            "author_nickname": "피요키 공식",
            "tags": ["일상", "공식"],
        },
    }
    deck["items"][0]["localizations"] = {
        "en": {"reading": "annyeong", "meaning": "hello"},
        "ko": {"reading": "annyeong", "meaning": "안녕하세요"},
    }
    return deck


class DeckValidationTests(unittest.TestCase):
    def test_valid_deck_only_warns_for_tts_fallback(self) -> None:
        issues = MODULE.validate_deck(sample_deck())
        self.assertFalse([value for value in issues if value["severity"] == "error"])
        self.assertIn("missing_audio", {value["code"] for value in issues})

    def test_rejects_unsupported_and_oversized_korean(self) -> None:
        deck = sample_deck()
        deck["items"].append({
            "id": "i_002",
            "ko": "가나다라마바사아자차카",
            "reading_ja": "カナダラマバサアジャチャカ",
            "meaning_ja": "長すぎる",
            "audio": None,
        })
        deck["items"].append({
            "id": "i_003",
            "ko": "한글ABC",
            "reading_ja": "ハングル",
            "meaning_ja": "英字入り",
            "audio": None,
        })
        codes = {value["code"] for value in MODULE.validate_deck(deck)}
        self.assertIn("max_target_length", codes)
        self.assertIn("undecomposable_ko", codes)

    def test_detects_duplicate_ids_and_targets(self) -> None:
        deck = sample_deck()
        deck["items"].append(dict(deck["items"][0]))
        issues = MODULE.validate_deck(deck)
        self.assertIn("duplicate", {value["code"] for value in issues})
        self.assertIn("duplicate_target", {value["code"] for value in issues})

    def test_accepts_optional_english_and_korean_localizations(self) -> None:
        issues = MODULE.validate_deck(add_localizations(sample_deck()))
        self.assertFalse([value for value in issues if value["severity"] == "error"])

    def test_rejects_malformed_localization_envelopes(self) -> None:
        deck = add_localizations(sample_deck())
        deck["localizations"]["fr"] = {
            "name": "Exemple",
            "author_nickname": "typee",
            "tags": ["Quotidien"],
            "extra": True,
        }
        del deck["items"][0]["localizations"]["en"]["meaning"]

        issues = MODULE.validate_deck(deck)
        codes = {value["code"] for value in issues if value["severity"] == "error"}
        self.assertIn("unsupported_locale", codes)
        self.assertIn("localized_tag_count", codes)
        self.assertIn("additional_property", codes)
        self.assertIn("required", codes)


class FakeHTTPResponse:
    def __init__(self, value: dict):
        self.payload = json.dumps(value).encode("utf-8")

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        return False

    def read(self) -> bytes:
        return self.payload


class AIContentServiceTests(unittest.TestCase):
    def test_generates_strict_structured_japanese_metadata(self) -> None:
        captured = {}

        def requester(request, timeout):
            captured["timeout"] = timeout
            captured["authorization"] = request.get_header("Authorization")
            captured["body"] = json.loads(request.data)
            return FakeHTTPResponse({
                "output": [{
                    "type": "message",
                    "content": [{
                        "type": "output_text",
                        "text": json.dumps({
                            "reading_ja": "アンニョンハセヨ",
                            "meaning_ja": "こんにちは",
                        }),
                    }],
                }],
            })

        service = MODULE.OpenAIContentService(
            api_key="test-key",
            model="test-model",
            requester=requester,
        )
        result = service.generate("안녕하세요", deck_name="あいさつ", deck_type="sentence")

        self.assertEqual(result["reading_ja"], "アンニョンハセヨ")
        self.assertEqual(result["meaning_ja"], "こんにちは")
        self.assertEqual(captured["authorization"], "Bearer test-key")
        self.assertEqual(captured["body"]["model"], "test-model")
        self.assertFalse(captured["body"]["store"])
        self.assertEqual(captured["body"]["reasoning"], {"effort": "none"})
        self.assertEqual(captured["body"]["text"]["format"]["type"], "json_schema")
        self.assertTrue(captured["body"]["text"]["format"]["strict"])

    def test_requires_server_side_api_key(self) -> None:
        service = MODULE.OpenAIContentService(api_key="")
        with self.assertRaisesRegex(MODULE.RequestError, "API 키"):
            service.generate("안녕")


class GTTSPronunciationServiceTests(unittest.TestCase):
    def test_generates_content_addressed_mp3_atomically_and_reuses_it(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            generator = MODULE._load_gtts_generator()
            calls = []

            class FakeGTTS:
                def __init__(self, **kwargs):
                    calls.append(kwargs)

                def save(self, path):
                    Path(path).write_bytes(b"ID3" + b"0" * 2_000)

            service = MODULE.GTTSPronunciationService(
                root,
                attempts=1,
                generator_loader=lambda: generator,
                tts_factory=FakeGTTS,
            )
            with mock.patch.object(MODULE.importlib.metadata, "version", return_value="2.5.4"), mock.patch.object(
                generator.shutil, "which", return_value=None
            ):
                first = service.generate("안녕")
                second = service.generate("안녕")

            self.assertTrue(first["generated"])
            self.assertFalse(second["generated"])
            self.assertEqual(first["audio_engine"], "gTTS 2.5.4 · ko · 보통 속도")
            self.assertRegex(first["audio"], r"^audio/ko_[a-f0-9]{20}\.mp3$")
            self.assertTrue((root / first["audio"]).exists())
            self.assertFalse((root / first["audio"]).with_suffix(".partial.mp3").exists())
            self.assertEqual(len(calls), 1)
            self.assertEqual(
                calls[0],
                {"text": "안녕", "lang": "ko", "tld": "com", "slow": False},
            )

    def test_missing_pinned_dependency_does_not_load_generator(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            loader = mock.Mock(side_effect=AssertionError("generator should stay lazy"))
            service = MODULE.GTTSPronunciationService(Path(directory), generator_loader=loader)
            with mock.patch.object(
                MODULE.importlib.metadata,
                "version",
                side_effect=MODULE.importlib.metadata.PackageNotFoundError,
            ):
                self.assertFalse(service.available)
                with self.assertRaisesRegex(MODULE.RequestError, "requirements-audio.txt"):
                    service.generate("안녕")
            loader.assert_not_called()

    def test_wrong_dependency_version_does_not_load_generator(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            loader = mock.Mock(side_effect=AssertionError("generator should stay lazy"))
            service = MODULE.GTTSPronunciationService(Path(directory), generator_loader=loader)
            with mock.patch.object(MODULE.importlib.metadata, "version", return_value="2.5.3"):
                self.assertFalse(service.available)
                with self.assertRaisesRegex(MODULE.RequestError, "현재 2.5.3"):
                    service.generate("안녕")
            loader.assert_not_called()

    def test_invalid_download_never_replaces_target_or_leaves_partial_file(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            generator = MODULE._load_gtts_generator()

            class EmptyGTTS:
                def __init__(self, **kwargs):
                    pass

                def save(self, path):
                    Path(path).write_bytes(b"too small")

            service = MODULE.GTTSPronunciationService(
                root,
                attempts=1,
                generator_loader=lambda: generator,
                tts_factory=EmptyGTTS,
            )
            expected = root / generator.audio_asset_path("안녕")
            expected.parent.mkdir(parents=True)
            original = b"existing invalid audio"
            expected.write_bytes(original)
            with mock.patch.object(MODULE.importlib.metadata, "version", return_value="2.5.4"), mock.patch.object(
                generator.shutil, "which", return_value=None
            ):
                with self.assertRaisesRegex(MODULE.RequestError, "MP3를 생성하지 못했습니다"):
                    service.generate("안녕")

            self.assertEqual(expected.read_bytes(), original)
            self.assertFalse(expected.with_suffix(".partial.mp3").exists())


class DeckStoreTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.catalog_root = self.root / "shared" / "mock_catalog"
        self.decks_root = self.catalog_root / "decks"
        self.decks_root.mkdir(parents=True)
        deck = sample_deck()
        payload = MODULE.json_bytes(deck)
        (self.decks_root / "official_sample_v1.json").write_bytes(payload)
        catalog = {
            "catalog_version": 3,
            "generated_at": "2026-07-20T00:00:00Z",
            "decks": [{
                "deck_id": deck["deck_id"],
                "version": 1,
                "name": deck["name"],
                "author_nickname": deck["author"]["nickname"],
                "official": True,
                "featured": False,
                "type": "word",
                "level": 1,
                "tags": deck["tags"],
                "item_count": 1,
                "size_bytes": len(payload),
                "downloads_total": 0,
                "downloads_7d": 0,
                "created_at": deck["created_at"],
                "preview_items": [{"ko": "안녕", "meaning_ja": "こんにちは"}],
                "file_url": "decks/official_sample_v1.json",
            }],
            "tags": [
                {"tag": "公式", "deck_count": 1, "category": "topic"},
                {"tag": "日常", "deck_count": 1, "category": "purpose"},
            ],
        }
        (self.catalog_root / "catalog.json").write_bytes(MODULE.json_bytes(catalog))
        self.store = MODULE.DeckStore(self.root)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def test_lists_catalog_deck(self) -> None:
        state = self.store.list_decks()
        self.assertEqual(state["catalog_version"], 3)
        self.assertEqual(len(state["decks"]), 1)
        self.assertTrue(state["decks"][0]["catalog_visible"])

    def test_saves_new_version_and_updates_catalog_and_override(self) -> None:
        deck = self.store.load_deck("official_sample_v1.json")
        deck["items"][0]["meaning_ja"] = "やあ"
        result = self.store.save({
            "deck": deck,
            "source_path": "official_sample_v1.json",
            "folder": "",
            "catalog_visible": True,
            "featured": True,
            "bump_version": True,
        })
        self.assertEqual(result["deck"]["version"], 2)
        self.assertEqual(result["path"], "official_sample_v2.json")
        self.assertTrue((self.decks_root / result["path"]).exists())
        self.assertTrue((self.root / "shared/deck_overrides/main/official_sample.json").exists())
        catalog = json.loads((self.catalog_root / "catalog.json").read_text(encoding="utf-8"))
        self.assertEqual(catalog["catalog_version"], 4)
        self.assertEqual(catalog["decks"][0]["version"], 2)
        self.assertEqual(catalog["decks"][0]["preview_items"][0]["meaning_ja"], "やあ")
        self.assertEqual(
            catalog["decks"][0]["size_bytes"],
            (self.decks_root / result["path"]).stat().st_size,
        )

    def test_preserves_localizations_in_deck_override_and_catalog(self) -> None:
        deck = add_localizations(self.store.load_deck("official_sample_v1.json"))
        result = self.store.save({
            "deck": deck,
            "source_path": "official_sample_v1.json",
            "folder": "",
            "catalog_visible": True,
            "featured": False,
            "bump_version": True,
        })

        saved_deck = json.loads((self.decks_root / result["path"]).read_text(encoding="utf-8"))
        override = json.loads(
            (self.root / "shared/deck_overrides/main/official_sample.json").read_text(encoding="utf-8")
        )
        catalog = json.loads((self.catalog_root / "catalog.json").read_text(encoding="utf-8"))
        entry = catalog["decks"][0]
        tags = {value["tag"]: value for value in catalog["tags"]}

        self.assertEqual(saved_deck["localizations"], deck["localizations"])
        self.assertEqual(saved_deck["items"][0]["localizations"], deck["items"][0]["localizations"])
        self.assertEqual(override, saved_deck)
        self.assertEqual(entry["localizations"], deck["localizations"])
        self.assertEqual(
            entry["preview_items"][0]["localizations"],
            {
                "en": {"meaning": "hello"},
                "ko": {"meaning": "안녕하세요"},
            },
        )
        self.assertEqual(tags["日常"]["localizations"], {"en": "Everyday", "ko": "일상"})
        self.assertEqual(tags["公式"]["localizations"], {"en": "Official", "ko": "공식"})

    def test_rejects_path_escape(self) -> None:
        with self.assertRaises(MODULE.RequestError):
            self.store.load_deck("../catalog.json")


if __name__ == "__main__":
    unittest.main()
