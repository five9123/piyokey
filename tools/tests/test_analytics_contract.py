import importlib.util
import json
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


if __name__ == "__main__":
    unittest.main()
