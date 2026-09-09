import importlib.util
import ast
import json
import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "gen_analytics_contract", ROOT / "tools/gen_analytics_contract.py"
)
assert SPEC and SPEC.loader
GENERATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GENERATOR)


class AnalyticsContractTests(unittest.TestCase):
    def test_contract_is_valid_and_generated_files_are_current(self):
        contract = GENERATOR.load_contract()
        self.assertEqual(1, contract["schema_version"])
        self.assertGreaterEqual(len(contract["events"]), 10)
        rendered = {
            "swift": GENERATOR.render_swift(contract),
            "kotlin": GENERATOR.render_kotlin(contract),
            "typescript": GENERATOR.render_typescript(contract),
        }
        for key, path in GENERATOR.OUTPUTS.items():
            self.assertEqual(rendered[key], path.read_text(encoding="utf-8"))

    def test_sensitive_and_free_text_properties_are_not_allowlisted(self):
        contract = json.loads((ROOT / "shared/analytics/events.json").read_text())
        allowed = set(contract["properties"])
        forbidden = set(contract["forbidden_properties"])
        self.assertFalse(allowed & forbidden)
        self.assertTrue({"text", "input", "answer", "user_deck_id", "path", "receipt"} <= forbidden)

    def test_every_event_has_the_privacy_common_properties(self):
        contract = GENERATOR.load_contract()
        common = set(contract["common_required"])
        for event, allowed in GENERATOR.event_map(contract).items():
            with self.subTest(event=event):
                self.assertTrue(common <= set(allowed))

    def test_every_enum_has_a_closed_nonempty_value_set(self):
        contract = GENERATOR.load_contract()
        for name, values in GENERATOR.enum_map(contract).items():
            with self.subTest(property=name):
                self.assertTrue(values)
                self.assertEqual(len(values), len(set(values)))

    def test_every_platform_disables_posthog_geoip(self):
        sources = (
            ROOT / "ios/Hanco/Hanco/Core/Analytics/TelemetryService.swift",
            ROOT / "android/app/src/main/java/app/piyokey/piyokey/TelemetryRuntime.kt",
            ROOT / "web/analytics/src/index.ts",
        )
        for source in sources:
            with self.subTest(source=source):
                self.assertIn("$geoip_disable", source.read_text(encoding="utf-8"))

    def test_usage_context_transform_preserves_exact_event_contract(self):
        source = (ROOT / "release/analytics/posthog_usage_context.hog").read_text()
        literal = source.split("let allowedProperties := ", 1)[1].split("\nlet eventKeys", 1)[0]
        self.assertEqual(GENERATOR.event_map(GENERATOR.load_contract()), ast.literal_eval(literal))

    def test_usage_context_allowlist_matches_client_and_ingestion(self):
        contract = json.loads((ROOT / "release/analytics/usage_context.json").read_text())
        swift = (ROOT / "ios/Hanco/Hanco/Core/Analytics/TelemetryService.swift").read_text()
        literal = swift.split("static let usageContextProperties: Set<String> = [", 1)[1].split("]", 1)[0]
        self.assertEqual(set(contract["sdk_properties"]), set(re.findall(r'"([^\"]+)"', literal)))
        hog = (ROOT / "release/analytics/posthog_usage_context.hog").read_text()
        hog_keys = hog.split("let contextKeys := ", 1)[1].split("\n", 1)[0]
        self.assertEqual(contract["sdk_properties"], ast.literal_eval(hog_keys))
        self.assertEqual(2, contract["notice_version"])
        self.assertEqual({"$geoip_country_name", "$geoip_country_code"},
                         set(contract["geoip_properties"]))

    def test_versioned_optional_privacy_notice_contract_is_documented(self):
        prd = (ROOT / "PRD.md").read_text(encoding="utf-8")
        analytics = (ROOT / "docs/ANALYTICS.md").read_text(encoding="utf-8")
        self.assertIn("고지 버전", prd)
        self.assertIn("Participate and continue", analytics)
        self.assertIn("Continue without sharing", analytics)
        self.assertIn("Settings keeps the two independent toggles", analytics)
        settings_source = (ROOT / "ios/Hanco/Hanco/Core/Settings/AppSettings.swift").read_text()
        policy = settings_source.split("enum PrivacyNoticePolicy {", 1)[1]
        version = re.search(r"static let currentVersion = (\d+)", policy).group(1)
        settings_prd = prd.split("### F10.", 1)[1].split("\n### ", 1)[0]
        documented_versions = re.findall(r"`PrivacyNoticePolicy\.currentVersion`[은는] (\d+)", settings_prd)
        self.assertEqual({version}, set(documented_versions))
        self.assertIn(f"PrivacyNoticePolicy.currentVersion` is {version}", analytics)


if __name__ == "__main__":
    unittest.main()
