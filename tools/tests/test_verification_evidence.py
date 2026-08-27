import importlib.util
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "tools/verification_evidence.py"
SPEC = importlib.util.spec_from_file_location("verification_evidence", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
verification_evidence = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = verification_evidence
SPEC.loader.exec_module(verification_evidence)


class VerificationEvidenceTests(unittest.TestCase):
    def test_parse_check_accepts_optional_note(self):
        self.assertEqual(
            verification_evidence.parse_check(
                "doctor::pass::python3 tools/workspace_doctor.py::clean"
            ),
            {
                "name": "doctor",
                "status": "pass",
                "command": "python3 tools/workspace_doctor.py",
                "note": "clean",
            },
        )

    def test_parse_check_rejects_unknown_status(self):
        with self.assertRaises(ValueError):
            verification_evidence.parse_check("doctor::green::command")

    def test_record_fails_if_any_check_fails(self):
        record = verification_evidence.build_record(
            scope="repository",
            commit="a" * 40,
            tree="b" * 40,
            branch="codex/59-task",
            checks=[
                {"name": "one", "status": "pass", "command": "one", "note": ""},
                {"name": "two", "status": "fail", "command": "two", "note": ""},
            ],
            manual_gates=["device"],
        )
        self.assertEqual(record["result"], "fail")
        self.assertEqual(record["manual_gates"], ["device"])


if __name__ == "__main__":
    unittest.main()
