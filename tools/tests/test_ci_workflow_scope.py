import fnmatch
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WORKFLOWS = ROOT / ".github" / "workflows"
PR_WORKFLOWS = {
    "source": WORKFLOWS / "source-ci.yml",
    "swift": WORKFLOWS / "swift-ci.yml",
    "ios": WORKFLOWS / "ios-ci.yml",
    "android": WORKFLOWS / "android-ci.yml",
}


def pull_request_paths(workflow: Path) -> list[str]:
    lines = workflow.read_text(encoding="utf-8").splitlines()
    in_pull_request = False
    in_paths = False
    result: list[str] = []

    for line in lines:
        if line == "  pull_request:":
            in_pull_request = True
            continue
        if in_pull_request and line.startswith("  ") and not line.startswith("    "):
            break
        if in_pull_request and line == "    paths:":
            in_paths = True
            continue
        if in_paths:
            match = re.fullmatch(r"      - ['\"](.+)['\"]", line)
            if match:
                result.append(match.group(1))
                continue
            if line and not line.startswith("      "):
                break

    if not result:
        raise AssertionError(f"No pull_request paths found in {workflow}")
    return result


def workflows_for(path: str) -> set[str]:
    return {
        name
        for name, workflow in PR_WORKFLOWS.items()
        if any(fnmatch.fnmatchcase(path, pattern) for pattern in pull_request_paths(workflow))
    }


class CIWorkflowScopeTests(unittest.TestCase):
    def test_path_routing_contract(self) -> None:
        cases = {
            "docs/WORKFLOW.md": set(),
            "PROJECT_STATUS.md": set(),
            "release/app_store_submission.json": {"source"},
            "PRD.md": {"source"},
            "ios/Hanco/Hanco/App.swift": {"source", "ios"},
            "ios/HangulEngine/Sources/DeckKit/Deck.swift": {"source", "swift", "ios"},
            "android/app/build.gradle.kts": {"source", "android"},
            "shared/schema/deck.schema.json": {"source", "swift", "ios", "android"},
            ".github/workflows/ios-ci.yml": {"source", "ios"},
            ".github/workflows/platform-regression.yml": {"source"},
        }

        for path, expected in cases.items():
            with self.subTest(path=path):
                self.assertEqual(workflows_for(path), expected)

    def test_all_actions_are_pinned_to_full_commit_shas(self) -> None:
        uses_line = re.compile(r"^\s*- uses: ([^@\s]+)@([^\s]+)")
        full_sha = re.compile(r"^[0-9a-f]{40}$")

        for workflow in sorted(WORKFLOWS.glob("*.yml")):
            for line_number, line in enumerate(
                workflow.read_text(encoding="utf-8").splitlines(), start=1
            ):
                match = uses_line.match(line)
                if match:
                    self.assertRegex(
                        match.group(2),
                        full_sha,
                        f"{workflow.name}:{line_number} must pin {match.group(1)}",
                    )

    def test_scheduled_regression_has_manual_dispatch_and_device_suites(self) -> None:
        regression = (WORKFLOWS / "platform-regression.yml").read_text(encoding="utf-8")
        self.assertIn("  schedule:", regression)
        self.assertIn("  workflow_dispatch:", regression)
        self.assertIn("xcodebuild test", regression)
        self.assertIn(":core:data:connectedDebugAndroidTest", regression)
        self.assertIn(":feature:practice:connectedDebugAndroidTest", regression)
        self.assertIn(":app:connectedDebugAndroidTest", regression)


if __name__ == "__main__":
    unittest.main()
