import json
import tempfile
import unittest
from pathlib import Path

from tools.gen_gtts_audio import audio_asset_path, audio_items, prune_stale_assets


class GTTSDeckAudioTests(unittest.TestCase):
    def write_deck(self, root: Path, relative_path: str, items: list[dict]) -> None:
        path = root / relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps({"items": items}), encoding="utf-8")

    def test_audio_items_collects_every_deck_and_deduplicates_prompts(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shared_path = audio_asset_path("안녕")
            friend_path = audio_asset_path("친구")
            sample_path = audio_asset_path("최고예요")
            shared = {"id": "i_001", "ko": "안녕", "audio": shared_path}
            self.write_deck(root, "decks/dictation/official.json", [shared])
            self.write_deck(
                root,
                "updates/decks/community.json",
                [shared, {"id": "i_002", "ko": "친구", "audio": friend_path}],
            )
            (root / "pronunciation_prompts.json").write_text(
                json.dumps({"schema_version": 1, "prompts": ["최고예요"]}),
                encoding="utf-8",
            )

            self.assertEqual(
                audio_items(root),
                {shared_path: "안녕", friend_path: "친구", sample_path: "최고예요"},
            )

    def test_audio_items_rejects_missing_or_unknown_paths(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_deck(
                root,
                "decks/deck.json",
                [{"id": "i_001", "ko": "안녕", "audio": "audio/ko_old.wav"}],
            )

            with self.assertRaisesRegex(RuntimeError, "unsafe gTTS audio path"):
                audio_items(root)

    def test_audio_items_rejects_hash_collisions(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_deck(
                root,
                "decks/deck.json",
                [
                    {"id": "i_001", "ko": "안녕", "audio": audio_asset_path("안녕")},
                    {"id": "i_002", "ko": "친구", "audio": audio_asset_path("안녕")},
                ],
            )

            with self.assertRaisesRegex(RuntimeError, "audio hash collision"):
                audio_items(root)

    def test_prune_only_removes_unreferenced_generated_audio(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            audio = root / "audio"
            audio.mkdir()
            kept = audio / "ko_kept.mp3"
            stale_mp3 = audio / "ko_stale.mp3"
            stale_caf = audio / "ko_stale.caf"
            unrelated = audio / "readme.txt"
            for path in (kept, stale_mp3, stale_caf, unrelated):
                path.write_bytes(b"test")

            removed = prune_stale_assets(root, {"audio/ko_kept.mp3"})

            self.assertEqual(removed, 2)
            self.assertTrue(kept.exists())
            self.assertFalse(stale_caf.exists())
            self.assertTrue(unrelated.exists())

    def test_audio_items_rejects_noncanonical_paths(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_deck(
                root,
                "decks/deck.json",
                [{"id": "i_001", "ko": "안녕", "audio": "audio/ko_wrong.mp3"}],
            )

            with self.assertRaisesRegex(RuntimeError, "non-canonical gTTS audio path"):
                audio_items(root)

    def test_release_generation_requires_supplemental_prompt_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_deck(
                root,
                "decks/deck.json",
                [{"id": "i_001", "ko": "안녕", "audio": audio_asset_path("안녕")}],
            )

            with self.assertRaisesRegex(RuntimeError, "missing pronunciation prompt manifest"):
                audio_items(root, require_prompt_manifest=True)


if __name__ == "__main__":
    unittest.main()
