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
                "https://github.com/five9123-maker/piyokey.git"
            ),
            workspace_doctor.EXPECTED_REMOTE,
        )
        self.assertEqual(
            workspace_doctor.normalize_remote(
                "git@github.com:five9123-maker/piyokey.git"
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
