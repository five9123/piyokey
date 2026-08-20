import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

import m4_ios_performance_gate as gate  # noqa: E402


class M4IOSPerformanceGateTests(unittest.TestCase):
    def test_parses_devicectl_payload_and_accepts_exact_baseline(self) -> None:
        payload = {
            "result": {
                "hardwareProperties": {
                    "marketingName": "iPhone 12",
                    "productType": "iPhone13,2",
                    "udid": "baseline-udid",
                },
                "deviceProperties": {
                    "name": "QA iPhone",
                    "osVersionNumber": "18.6",
                },
            }
        }

        device = gate.parse_device_info(payload)

        self.assertEqual(device.name, "QA iPhone")
        self.assertEqual(device.udid, "baseline-udid")
        self.assertTrue(device.is_prd_baseline)

    def test_rejects_nonbaseline_without_explicit_proxy_override(self) -> None:
        device = gate.DeviceInfo(
            name="JM",
            marketing_name="iPhone 15 Pro",
            product_type="iPhone16,1",
            udid="proxy-udid",
            os_version="26.5",
        )

        with self.assertRaises(SystemExit):
            gate.require_baseline_or_proxy(device, allow_proxy=False)
        gate.require_baseline_or_proxy(device, allow_proxy=True)

    def test_animation_hitches_attaches_only_to_hanco(self) -> None:
        command = gate.build_record_command(
            template="animation-hitches",
            device_udid="device-udid",
            duration_seconds=30,
            output=Path("animation.trace"),
        )

        self.assertIn("Animation Hitches", command)
        self.assertEqual(command[-2:], ["--attach", "Hanco"])
        self.assertNotIn("--all-processes", command)

    def test_game_performance_uses_windowed_all_process_recording(self) -> None:
        command = gate.build_record_command(
            template="game-performance",
            device_udid="device-udid",
            duration_seconds=20,
            output=Path("game.trace"),
        )

        self.assertIn("Game Performance", command)
        self.assertIn("--all-processes", command)
        self.assertIn("--window", command)
        self.assertEqual(command[command.index("--window") + 1], "20s")


if __name__ == "__main__":
    unittest.main()
