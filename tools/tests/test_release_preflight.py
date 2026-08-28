import copy
import importlib.util
import json
import struct
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "tools/release_preflight.py"
SPEC = importlib.util.spec_from_file_location("release_preflight", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
release_preflight = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = release_preflight
SPEC.loader.exec_module(release_preflight)


class ReleasePreflightTests(unittest.TestCase):
    def test_repository_checks_pass_for_current_bundle(self):
        self.assertEqual(release_preflight.repository_checks(ROOT), [])

    def test_png_info_detects_alpha(self):
        opaque = ROOT / "ios/Hanco/Hanco/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png"
        width, height, has_alpha = release_preflight.png_info(opaque)
        self.assertEqual((width, height), (1024, 1024))
        self.assertFalse(has_alpha)

        with tempfile.TemporaryDirectory() as directory:
            alpha = Path(directory) / "alpha.png"
            data = bytearray(opaque.read_bytes()[:33])
            data[25] = 6
            alpha.write_bytes(data)
            self.assertTrue(release_preflight.png_info(alpha)[2])

    def test_strict_checks_keep_unfinished_submission_blocked(self):
        messages = {finding.message for finding in release_preflight.strict_checks(ROOT)}
        self.assertNotIn("Prototype Bundle ID must be replaced", messages)
        self.assertNotIn("Submission Bundle ID must match the Xcode app target", messages)
        self.assertFalse(any("All targets must use release team" in message for message in messages))
        self.assertIn("Content rights are not confirmed", messages)
        self.assertIn("QA gate is open: silent_mode_sound_device_check", messages)
        self.assertIn("QA gate is open: iphone_12_performance_gate", messages)
        self.assertIn("QA gate is open: game_center_live_device_check", messages)
        self.assertIn("In-app purchase gate is open: paid_apps_agreement_active", messages)
        self.assertIn("In-app purchase gate is open: review_screenshot_uploaded", messages)
        self.assertIn("In-app purchase gate is open: ready_to_submit", messages)
        self.assertIn("In-app purchase gate is open: attached_to_version_1_1_submission", messages)
        self.assertNotIn("Next submission record must target version 1.1", messages)
        self.assertNotIn("Submission marketing version must match Xcode", messages)
        self.assertNotIn("Submission build number must match Xcode", messages)
        self.assertIn("Next submission release mode must be resolved independently of availability", messages)

    def test_deck_maker_storekit_configuration_is_repository_valid(self):
        messages = {finding.message for finding in release_preflight.repository_checks(ROOT)}
        self.assertFalse(any("DeckMaker.storekit" in message for message in messages))
        self.assertFalse(any("StoreKit test" in message for message in messages))
        self.assertNotIn("The local StoreKit configuration must not be embedded in the app bundle", messages)
        self.assertNotIn("The shared scheme LaunchAction using local StoreKit must remain Debug-only", messages)
        self.assertNotIn("Debug LaunchAction must select DeckMaker.storekit", messages)
        self.assertNotIn("ArchiveAction must not use a local StoreKit configuration", messages)
        self.assertNotIn("Release ProfileAction must not use a local StoreKit configuration", messages)

    def test_game_center_contract_rejects_duplicate_and_unknown_ids(self):
        findings = release_preflight.game_center_contract_findings(
            {
                release_preflight.GAME_CENTER_AVAILABILITY_KEY: [
                    "piyokey.live.board",
                    "piyokey.live.board",
                    "piyokey.unknown.board",
                ],
                release_preflight.GAME_CENTER_INTENDED_KEY: [
                    "piyokey.live.board",
                    "piyokey.unknown.board",
                ],
            },
            'enum GameCenterLeaderboard { case live = "piyokey.live.board" }\nenum PiyoCupWeek {}',
        )
        messages = {finding.message for finding in findings}
        self.assertIn("Game Center availability IDs must be unique", messages)
        self.assertTrue(any("piyokey.unknown.board" in message for message in messages))

    def test_ios_universal_contract_is_repository_valid(self):
        project = (ROOT / "ios/Hanco/Hanco.xcodeproj/project.pbxproj").read_text(encoding="utf-8")
        info = release_preflight.load_plist(ROOT / "ios/Hanco/Hanco/Resources/Info.plist")
        self.assertEqual(release_preflight.ios_universal_contract_findings(project, info), [])

    def test_ios_universal_contract_rejects_device_orientation_and_window_drift(self):
        project = "TARGETED_DEVICE_FAMILY = 1;"
        info = {
            "UISupportedInterfaceOrientations": [
                "UIInterfaceOrientationPortrait",
                "UIInterfaceOrientationLandscapeLeft",
            ],
            "UISupportedInterfaceOrientations~ipad": ["UIInterfaceOrientationPortrait"],
            "UIRequiresFullScreen": True,
        }
        messages = {
            finding.message
            for finding in release_preflight.ios_universal_contract_findings(project, info)
        }
        self.assertTrue(any("iPhone and iPad device families" in message for message in messages))
        self.assertTrue(any("iPhone orientations must remain portrait-only" in message for message in messages))
        self.assertTrue(any("iPad must declare all four" in message for message in messages))
        self.assertIn(
            "UIRequiresFullScreen must remain absent for iPad multitasking and resizable windows",
            messages,
        )

    def test_store_metadata_respects_apple_field_limits(self):
        messages = {finding.message for finding in release_preflight.repository_checks(ROOT)}
        self.assertFalse(any("store field" in message for message in messages))
        self.assertFalse(any("keywords exceed" in message for message in messages))
        self.assertFalse(any("characters" in message for message in messages))

    def test_global_app_store_metadata_contract_is_repository_valid(self):
        path = ROOT / "release/global_app_store_metadata.json"
        self.assertEqual(release_preflight.global_app_store_metadata_findings(path), [])

    def test_global_app_store_metadata_contract_rejects_scope_and_name_drift(self):
        source = json.loads((ROOT / "release/global_app_store_metadata.json").read_text(encoding="utf-8"))
        invalid = copy.deepcopy(source)
        invalid["app_record"]["planned_primary_locale"] = "ja"
        invalid["version_1_1_localization_scope"]["app_ui_locales"] = ["ja", "en", "ko"]
        invalid["version_1_1_localization_scope"]["preserved_content_locales"] = ["ja", "en"]
        invalid["version_1_1_localization_scope"]["unsupported_app_language_fallback"] = "ja"
        invalid["version_1_1_localization_scope"]["app_store_metadata_locales"] = ["ja", "en-US", "ko"]
        invalid["version_1_1_localization_scope"]["android_m7"] = "active"
        invalid["availability"]["selection"] = "SPECIFIC_COUNTRIES_OR_REGIONS"
        invalid["availability"]["include_future_storefronts"] = False
        invalid["availability"]["required_app_and_iap_match"] = False
        invalid["availability"]["country_waves_enabled"] = True
        invalid["release_gates"].pop("VN")
        invalid["localizations"].pop("en-CA")
        invalid["localizations"]["ja"]["name"] = "ピヨキー"
        invalid["localizations"]["en-US"]["name"] = "typee"
        invalid["localizations"]["ko"]["name"] = "피요키"

        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "global_app_store_metadata.json"
            path.write_text(json.dumps(invalid), encoding="utf-8")
            messages = {
                finding.message
                for finding in release_preflight.global_app_store_metadata_findings(path)
            }

        self.assertIn("Global planned primary locale must be en-US", messages)
        self.assertIn("Version 1.1 app UI locales must be exactly ja, en, es, de, and fr", messages)
        self.assertIn("Preserved learning content locales must remain ja, en, and ko", messages)
        self.assertIn("Unsupported app language fallback must be en", messages)
        self.assertIn(
            "Version 1.1 App Store metadata locales must be exactly en-US, en-GB, en-AU, en-CA, ko, and ja",
            messages,
        )
        self.assertIn("Android M7 must remain on its resumed, separate Google Play release track", messages)
        self.assertIn("Version 1.1 availability must select All Countries or Regions", messages)
        self.assertIn("Version 1.1 availability must include future storefronts", messages)
        self.assertIn("App and typee pro IAP availability must match", messages)
        self.assertIn("Country availability waves must remain disabled", messages)
        self.assertIn("Global availability requires a non-empty VN compliance gate list", messages)
        self.assertIn(
            "Global App Store localization keys must be exactly en-US, en-GB, en-AU, en-CA, ko, and ja",
            messages,
        )
        self.assertTrue(any(message.startswith("ja global App Store name") for message in messages))
        self.assertTrue(any(message.startswith("en-US global App Store name") for message in messages))
        self.assertTrue(any(message.startswith("en-CA global App Store name") for message in messages))
        self.assertTrue(any(message.startswith("ko global App Store name") for message in messages))

    def test_global_app_store_metadata_contract_rejects_missing_and_invalid_files(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "global_app_store_metadata.json"
            missing_messages = {
                finding.message
                for finding in release_preflight.global_app_store_metadata_findings(path)
            }
            self.assertTrue(any("is missing" in message for message in missing_messages))

            path.write_text("{invalid", encoding="utf-8")
            invalid_messages = {
                finding.message
                for finding in release_preflight.global_app_store_metadata_findings(path)
            }
            self.assertTrue(any(message.startswith("Invalid global App Store metadata:") for message in invalid_messages))

    def test_google_play_metadata_contract_is_repository_valid(self):
        path = ROOT / "release/google_play_metadata.json"
        self.assertEqual(release_preflight.google_play_metadata_findings(path, ROOT), [])

    def test_google_play_metadata_contract_rejects_listing_drift(self):
        source = json.loads((ROOT / "release/google_play_metadata.json").read_text(encoding="utf-8"))
        invalid = copy.deepcopy(source)
        invalid["release"]["target_sdk"] = 35
        invalid["localizations"].pop("ko")
        invalid["localizations"]["ja"]["title"] = "長" * 31
        invalid["localizations"]["en-US"]["short_description"] = "Download now"
        invalid["assets"]["phone_screenshots"]["status"] = "complete"
        invalid["external_gates"]["play_console_app_created"] = True

        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "google_play_metadata.json"
            path.write_text(json.dumps(invalid), encoding="utf-8")
            messages = {
                finding.message
                for finding in release_preflight.google_play_metadata_findings(path, ROOT)
            }

        self.assertIn("Google Play target SDK must be 36", messages)
        self.assertIn("Google Play localization keys must be exactly en-US, ja, and ko", messages)
        self.assertIn("ja Google Play title exceeds 30 characters", messages)
        self.assertIn("en-US Google Play short_description contains prohibited promotional copy", messages)
        self.assertIn("Google Play screenshots must remain pending the exact signed release candidate", messages)
        self.assertIn("Google Play external gates must remain open until verified outside the repository", messages)

    def test_google_play_metadata_contract_rejects_invalid_icon_dimensions(self):
        source = json.loads((ROOT / "release/google_play_metadata.json").read_text(encoding="utf-8"))
        with tempfile.TemporaryDirectory() as directory:
            temp_root = Path(directory)
            invalid_icon = temp_root / "invalid-icon.png"
            data = bytearray((ROOT / source["assets"]["app_icon"]["path"]).read_bytes()[:33])
            struct.pack_into(">I", data, 16, 511)
            invalid_icon.write_bytes(data)
            source["assets"]["app_icon"]["path"] = str(invalid_icon)
            source["assets"]["feature_graphic"]["path"] = str(
                ROOT / source["assets"]["feature_graphic"]["path"]
            )
            source["assets"]["feature_graphic"]["source_path"] = str(
                ROOT / source["assets"]["feature_graphic"]["source_path"]
            )
            path = temp_root / "google_play_metadata.json"
            path.write_text(json.dumps(source), encoding="utf-8")
            messages = {
                finding.message
                for finding in release_preflight.google_play_metadata_findings(path, temp_root)
            }

        self.assertIn("Google Play app_icon is 511x512, expected 512x512", messages)

    def test_google_play_console_draft_keeps_data_safety_unresolved(self):
        path = ROOT / "release/google_play_console_declarations.json"
        self.assertEqual(release_preflight.google_play_console_declaration_findings(path), [])
        invalid = json.loads(path.read_text(encoding="utf-8"))
        invalid["status"] = "applied"
        invalid["data_safety_evidence"]["final_collects_or_shares_answer"] = "NO"
        invalid["permissions_prohibited"] = []
        with tempfile.TemporaryDirectory() as directory:
            draft = Path(directory) / "google_play_console_declarations.json"
            draft.write_text(json.dumps(invalid), encoding="utf-8")
            messages = {
                finding.message
                for finding in release_preflight.google_play_console_declaration_findings(draft)
            }
        self.assertIn("Google Play Console declarations must remain operator-review drafts", messages)
        self.assertIn("Google Play Data safety must remain unresolved pending host and SDK review", messages)
        self.assertIn("Google Play prohibited permission contract differs", messages)

    def test_screenshot_manifest_matches_release_images(self):
        messages = {finding.message for finding in release_preflight.strict_checks(ROOT)}
        self.assertNotIn("Japanese screenshot manifest is missing", messages)
        self.assertNotIn("Screenshot manifest must list every PNG exactly once", messages)
        self.assertFalse(any("Screenshot checksum differs" in message for message in messages))


if __name__ == "__main__":
    unittest.main()
