import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "tools/workspace_doctor.py"
SPEC = importlib.util.spec_from_file_location("workspace_doctor", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
workspace_doctor = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = workspace_doctor
SPEC.loader.exec_module(workspace_doctor)


class WorkspaceDoctorTests(unittest.TestCase):
    def test_normalize_remote_accepts_https_and_ssh(self):
        self.assertEqual(
            workspace_doctor.normalize_remote(
                "https://github.com/five9123/piyokey.git"
            ),
            workspace_doctor.EXPECTED_REMOTE,
        )
        self.assertEqual(
            workspace_doctor.normalize_remote(
                "git@github.com:five9123/piyokey.git"
            ),
            workspace_doctor.EXPECTED_REMOTE,
        )

    def test_branch_status_rejects_detached_head(self):
        self.assertEqual(workspace_doctor.branch_status("HEAD")[0], "FAIL")
        self.assertEqual(workspace_doctor.branch_status("")[0], "FAIL")

    def test_branch_status_warns_on_main_and_accepts_task_branch(self):
        self.assertEqual(workspace_doctor.branch_status("main")[0], "WARN")
        self.assertEqual(
            workspace_doctor.branch_status("codex/123-device-setup")[0], "PASS"
        )

    def test_issue_from_branch_reads_codex_issue_number(self):
        self.assertEqual(
            workspace_doctor.issue_from_branch("codex/59-project-reorganization"),
            59,
        )
        self.assertIsNone(workspace_doctor.issue_from_branch("main"))

    def test_ownership_status_requires_matching_issue_branch_and_worktree(self):
        with tempfile.TemporaryDirectory() as directory:
            worktree = Path(directory)
            metadata = {
                "schema_version": 1,
                "issue": 59,
                "owner": "codex-task",
                "branch": "codex/59-project-reorganization",
                "worktree": str(worktree),
            }
            self.assertEqual(
                workspace_doctor.ownership_status(
                    "codex/59-project-reorganization", worktree, metadata
                )[0],
                "PASS",
            )
            metadata["issue"] = 58
            self.assertEqual(
                workspace_doctor.ownership_status(
                    "codex/59-project-reorganization", worktree, metadata
                )[0],
                "FAIL",
            )

    def test_ownership_status_warns_when_unclaimed(self):
        self.assertEqual(
            workspace_doctor.ownership_status("codex/59-task", ROOT, None)[0],
            "WARN",
        )

    def test_ownership_status_accepts_unclaimed_main_baseline(self):
        self.assertEqual(
            workspace_doctor.ownership_status("main", ROOT, None)[0],
            "PASS",
        )

    def test_python_version_requires_3_11(self):
        self.assertEqual(workspace_doctor.python_version_status((3, 10, 9))[0], "FAIL")
        self.assertEqual(workspace_doctor.python_version_status((3, 11, 0))[0], "PASS")
        self.assertEqual(workspace_doctor.python_version_status((3, 13, 1))[0], "PASS")

    def test_java_version_requires_17(self):
        self.assertEqual(
            workspace_doctor.java_version_status('openjdk version "16.0.2"')[0],
            "FAIL",
        )
        self.assertEqual(
            workspace_doctor.java_version_status('openjdk version "17.0.12"')[0],
            "PASS",
        )
        self.assertEqual(
            workspace_doctor.java_version_status('openjdk version "21.0.8"')[0],
            "PASS",
        )
        self.assertEqual(workspace_doctor.java_version_status("unknown")[0], "FAIL")

    def test_android_sdk_path_prefers_configured_then_default(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            configured = home / "configured-sdk"
            default = home / "Library/Android/sdk"
            configured.mkdir()
            default.mkdir(parents=True)
            self.assertEqual(
                workspace_doctor.android_sdk_path(
                    {"ANDROID_HOME": str(configured)}, home
                ),
                configured,
            )
            self.assertEqual(workspace_doctor.android_sdk_path({}, home), default)

    def test_android_platform_path_accepts_versioned_sdk_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            sdk = Path(directory)
            versioned = sdk / "platforms/android-37.0"
            versioned.mkdir(parents=True)
            self.assertEqual(
                workspace_doctor.android_platform_path(sdk, 37), versioned
            )
            self.assertIsNone(workspace_doctor.android_platform_path(sdk, 36))


if __name__ == "__main__":
    unittest.main()
