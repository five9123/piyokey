import json
import tempfile
import unittest
from pathlib import Path

from tools.gen_local_speech_audio import local_audio_items, prune_stale_assets


class LocalSpeechDeckAudioTests(unittest.TestCase):
    def write_deck(self, root: Path, relative_path: str, items: list[dict]) -> None:
        path = root / relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps({"items": items}), encoding="utf-8")

    def test_local_audio_items_collects_caf_and_ignores_mp3(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            caf = {"id": "i_001", "ko": "안녕", "audio": "audio/ko_local.caf"}
            mp3 = {"id": "i_002", "ko": "친구", "audio": "audio/ko_remote.mp3"}
            self.write_deck(root, "decks/official.json", [caf, mp3])
            self.write_deck(root, "updates/decks/official.json", [caf, mp3])

            self.assertEqual(local_audio_items(root), {"audio/ko_local.caf": "안녕"})

    def test_local_audio_items_rejects_unknown_paths(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_deck(
                root,
                "decks/deck.json",
                [{"id": "i_001", "ko": "안녕", "audio": "../ko_local.caf"}],
            )

            with self.assertRaisesRegex(RuntimeError, "unsafe local audio path"):
                local_audio_items(root)

    def test_local_audio_items_rejects_hash_collisions(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_deck(
                root,
                "decks/deck.json",
                [
                    {"id": "i_001", "ko": "안녕", "audio": "audio/ko_local.caf"},
                    {"id": "i_002", "ko": "친구", "audio": "audio/ko_local.caf"},
                ],
            )

            with self.assertRaisesRegex(RuntimeError, "audio hash collision"):
                local_audio_items(root)

    def test_prune_only_removes_unreferenced_local_caf(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            audio = root / "audio"
            audio.mkdir()
            kept = audio / "ko_kept.caf"
            stale_caf = audio / "ko_stale.caf"
            unrelated_mp3 = audio / "ko_remote.mp3"
            for path in (kept, stale_caf, unrelated_mp3):
                path.write_bytes(b"test")

            removed = prune_stale_assets(root, {"audio/ko_kept.caf"})

            self.assertEqual(removed, 1)
            self.assertTrue(kept.exists())
            self.assertTrue(unrelated_mp3.exists())


if __name__ == "__main__":
    unittest.main()
