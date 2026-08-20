#!/usr/bin/env python3
"""Prepare and record the physical-device performance gate for Hanco M4."""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import tempfile
import time
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Sequence


ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / "ios" / "Hanco" / "Hanco.xcodeproj"
DEFAULT_DERIVED_DATA = Path("/tmp/HancoM4PerformanceDerivedData")
DEFAULT_ARTIFACTS = ROOT / "artifacts" / "m4"
BUNDLE_IDENTIFIER = "com.hanco.prototype"


@dataclass(frozen=True)
class DeviceInfo:
    name: str
    marketing_name: str
    product_type: str
    udid: str
    os_version: str

    @property
    def is_prd_baseline(self) -> bool:
        return self.marketing_name == "iPhone 12" or self.product_type == "iPhone13,2"


def parse_device_info(payload: dict[str, Any]) -> DeviceInfo:
    try:
        result = payload["result"]
        hardware = result["hardwareProperties"]
        device = result["deviceProperties"]
        return DeviceInfo(
            name=device["name"],
            marketing_name=hardware["marketingName"],
            product_type=hardware["productType"],
            udid=hardware["udid"],
            os_version=device["osVersionNumber"],
        )
    except (KeyError, TypeError) as error:
        raise ValueError("Unexpected devicectl device-info JSON") from error


def run_checked(command: Sequence[str], *, cwd: Path = ROOT) -> None:
    print("$", " ".join(command), flush=True)
    subprocess.run(command, cwd=cwd, check=True)


def query_device_info(device: str) -> DeviceInfo:
    with tempfile.TemporaryDirectory(prefix="hanco-m4-device-") as temporary_directory:
        output = Path(temporary_directory) / "device.json"
        run_checked(
            [
                "xcrun",
                "devicectl",
                "device",
                "info",
                "details",
                "--device",
                device,
                "--json-output",
                str(output),
            ]
        )
        return parse_device_info(json.loads(output.read_text(encoding="utf-8")))


def require_baseline_or_proxy(device: DeviceInfo, allow_proxy: bool) -> None:
    if device.is_prd_baseline or allow_proxy:
        return
    raise SystemExit(
        f"{device.marketing_name} ({device.product_type}) is not the PRD iPhone 12 "
        "baseline. Re-run with --allow-proxy only for auxiliary evidence."
    )


def slug(value: str) -> str:
    normalized = re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-")
    return normalized or "device"


def timestamp() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


def write_metadata(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def common_metadata(device: DeviceInfo, allow_proxy: bool) -> dict[str, Any]:
    return {
        "recorded_at": datetime.now(timezone.utc).isoformat(),
        "device": asdict(device),
        "prd_baseline": device.is_prd_baseline,
        "proxy_override": allow_proxy and not device.is_prd_baseline,
    }


def prepare_device(args: argparse.Namespace) -> None:
    device = query_device_info(args.device)
    require_baseline_or_proxy(device, args.allow_proxy)

    build_command = [
        "xcodebuild",
        "build",
        "-quiet",
        "-project",
        str(PROJECT),
        "-scheme",
        "Hanco",
        "-configuration",
        "Debug",
        "-destination",
        f"platform=iOS,id={device.udid}",
        "-derivedDataPath",
        str(args.derived_data),
        "-allowProvisioningUpdates",
    ]
    if args.development_team:
        build_command.append(f"DEVELOPMENT_TEAM={args.development_team}")
    run_checked(build_command)

    app_path = args.derived_data / "Build" / "Products" / "Debug-iphoneos" / "Hanco.app"
    if not app_path.is_dir():
        raise SystemExit(f"Built app not found: {app_path}")

    run_checked(
        [
            "xcrun",
            "devicectl",
            "device",
            "install",
            "app",
            "--device",
            device.udid,
            str(app_path),
        ]
    )
    run_checked(
        [
            "xcrun",
            "devicectl",
            "device",
            "process",
            "launch",
            "--device",
            device.udid,
            "--terminate-existing",
            BUNDLE_IDENTIFIER,
        ]
    )

    metadata = common_metadata(device, args.allow_proxy)
    metadata.update({"action": "prepare", "app_path": str(app_path)})
    output = args.artifacts / f"m4-{slug(device.marketing_name)}-preparation-{timestamp()}.json"
    write_metadata(output, metadata)
    print(f"Prepared {device.marketing_name} / iOS {device.os_version}")
    print(f"Metadata: {output}")
    print("Open the first game deck, then use the record command while completing two flawless cards.")


def build_record_command(
    *,
    template: str,
    device_udid: str,
    duration_seconds: int,
    output: Path,
) -> list[str]:
    template_name = {
        "animation-hitches": "Animation Hitches",
        "game-performance": "Game Performance",
    }[template]
    command = [
        "xcrun",
        "xctrace",
        "record",
        "--template",
        template_name,
        "--device",
        device_udid,
        "--time-limit",
        f"{duration_seconds}s",
        "--output",
        str(output),
        "--no-prompt",
    ]
    if template == "animation-hitches":
        command.extend(["--attach", "Hanco"])
    else:
        command.extend(["--window", f"{duration_seconds}s", "--all-processes"])
    return command


def record_trace(args: argparse.Namespace) -> None:
    device = query_device_info(args.device)
    require_baseline_or_proxy(device, args.allow_proxy)

    args.artifacts.mkdir(parents=True, exist_ok=True)
    output = args.output or (
        args.artifacts
        / f"hanco-{slug(device.marketing_name)}-{args.template}-{timestamp()}.trace"
    )
    if output.exists():
        raise SystemExit(f"Trace output already exists: {output}")

    print(
        f"Recording {args.template} on {device.marketing_name} in {args.countdown} seconds. "
        "Complete two flawless cards so both particle bursts occur during the trace.",
        flush=True,
    )
    for remaining in range(args.countdown, 0, -1):
        print(remaining, flush=True)
        time.sleep(1)

    run_checked(
        build_record_command(
            template=args.template,
            device_udid=device.udid,
            duration_seconds=args.duration,
            output=output,
        )
    )

    toc_output = output.with_suffix(".toc.xml")
    run_checked(
        [
            "xcrun",
            "xctrace",
            "export",
            "--input",
            str(output),
            "--toc",
            "--output",
            str(toc_output),
        ]
    )

    metadata = common_metadata(device, args.allow_proxy)
    metadata.update(
        {
            "action": "record",
            "template": args.template,
            "duration_seconds": args.duration,
            "scenario": args.note,
            "trace": str(output),
            "table_of_contents": str(toc_output),
        }
    )
    metadata_output = output.with_suffix(".metadata.json")
    write_metadata(metadata_output, metadata)
    print(f"Trace: {output}")
    print(f"TOC: {toc_output}")
    print(f"Metadata: {metadata_output}")
    if not device.is_prd_baseline:
        print("Proxy evidence recorded. It cannot close the PRD iPhone 12 gate.")


def inspect_device(args: argparse.Namespace) -> None:
    device = query_device_info(args.device)
    print(json.dumps({**asdict(device), "prd_baseline": device.is_prd_baseline}, indent=2))


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    inspect = subparsers.add_parser("inspect-device", help="Show device and baseline status")
    inspect.add_argument("--device", required=True, help="Device name, CoreDevice ID, or UDID")
    inspect.set_defaults(handler=inspect_device)

    prepare = subparsers.add_parser("prepare", help="Build, install, and launch Debug Hanco")
    prepare.add_argument("--device", required=True, help="Device name, CoreDevice ID, or UDID")
    prepare.add_argument("--allow-proxy", action="store_true")
    prepare.add_argument("--development-team", default=os.environ.get("DEVELOPMENT_TEAM"))
    prepare.add_argument("--derived-data", type=Path, default=DEFAULT_DERIVED_DATA)
    prepare.add_argument("--artifacts", type=Path, default=DEFAULT_ARTIFACTS)
    prepare.set_defaults(handler=prepare_device)

    record = subparsers.add_parser("record", help="Record one Instruments trace")
    record.add_argument("--device", required=True, help="Device name, CoreDevice ID, or UDID")
    record.add_argument(
        "--template",
        choices=("animation-hitches", "game-performance"),
        required=True,
    )
    record.add_argument("--duration", type=int, default=30)
    record.add_argument("--countdown", type=int, default=5)
    record.add_argument("--allow-proxy", action="store_true")
    record.add_argument("--artifacts", type=Path, default=DEFAULT_ARTIFACTS)
    record.add_argument("--output", type=Path)
    record.add_argument(
        "--note",
        default="Two flawless card completions with two particle bursts",
    )
    record.set_defaults(handler=record_trace)
    return parser


def main() -> None:
    args = build_parser().parse_args()
    if hasattr(args, "duration") and args.duration <= 0:
        raise SystemExit("--duration must be positive")
    if hasattr(args, "countdown") and args.countdown < 0:
        raise SystemExit("--countdown cannot be negative")
    args.handler(args)


if __name__ == "__main__":
    main()
