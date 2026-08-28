#!/usr/bin/env python3
"""Generate deterministic cross-platform `.typedeck` binary fixtures."""

from __future__ import annotations

import copy
import hashlib
import json
import os
import struct
import tempfile
from pathlib import Path

import piyodeck_tool


ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "shared/piyodeck/fixtures"
VALID = FIXTURES / "valid"
INVALID = FIXTURES / "invalid"

LOCAL_FILE_HEADER_SIGNATURE = 0x04034B50
CENTRAL_DIRECTORY_HEADER_SIGNATURE = 0x02014B50
END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054B50


def _manifest(deck: dict[str, object], deck_data: bytes) -> dict[str, object]:
    items = deck["items"]
    assert isinstance(items, list)
    return {
        "format": piyodeck_tool.FORMAT_IDENTIFIER,
        "format_version": piyodeck_tool.FORMAT_VERSION,
        "deck_schema_version": piyodeck_tool.DECK_SCHEMA_VERSION,
        "deck": {
            "path": "deck.json",
            "media_type": "application/json",
            "deck_id": deck["deck_id"],
            "deck_version": deck["version"],
            "item_count": len(items),
            "size_bytes": len(deck_data),
            "sha256": hashlib.sha256(deck_data).hexdigest(),
        },
    }


def _package(manifest: dict[str, object], deck_data: bytes) -> bytes:
    return piyodeck_tool.build_deterministic_zip(
        (
            ("manifest.json", piyodeck_tool.canonical_json(manifest)),
            ("deck.json", deck_data),
        )
    )


def _write(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(prefix=f".{path.name}.", dir=path.parent, delete=False) as file:
        file.write(data)
        file.flush()
        os.fsync(file.fileno())
        temporary = Path(file.name)
    os.replace(temporary, path)


def _case(identifier: str, path: Path, data: bytes, valid: bool, expectation: str) -> dict[str, object]:
    return {
        "id": identifier,
        "path": str(path.relative_to(FIXTURES)),
        "valid": valid,
        "expectation": expectation,
        "size_bytes": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
    }


def _set_u16(data: bytearray, offset: int, value: int) -> None:
    struct.pack_into("<H", data, offset, value)


def _set_u32(data: bytearray, offset: int, value: int) -> None:
    struct.pack_into("<I", data, offset, value)


def _zip_records(package: bytes) -> dict[str, dict[str, int]]:
    end_offset = len(package) - 22
    if struct.unpack_from("<I", package, end_offset)[0] != END_OF_CENTRAL_DIRECTORY_SIGNATURE:
        raise RuntimeError("fixture is missing its end-of-central-directory record")
    total_entries = struct.unpack_from("<H", package, end_offset + 10)[0]
    cursor = struct.unpack_from("<I", package, end_offset + 16)[0]
    records: dict[str, dict[str, int]] = {}
    for _ in range(total_entries):
        if struct.unpack_from("<I", package, cursor)[0] != CENTRAL_DIRECTORY_HEADER_SIGNATURE:
            raise RuntimeError("fixture has an invalid central-directory record")
        name_length, extra_length, comment_length = struct.unpack_from("<HHH", package, cursor + 28)
        local_offset = struct.unpack_from("<I", package, cursor + 42)[0]
        name = package[cursor + 46 : cursor + 46 + name_length].decode("ascii")
        if struct.unpack_from("<I", package, local_offset)[0] != LOCAL_FILE_HEADER_SIGNATURE:
            raise RuntimeError("fixture has an invalid local record")
        local_name_length, local_extra_length = struct.unpack_from(
            "<HH",
            package,
            local_offset + 26,
        )
        compressed_size = struct.unpack_from("<I", package, cursor + 20)[0]
        data_offset = local_offset + 30 + local_name_length + local_extra_length
        records[name] = {
            "central": cursor,
            "local": local_offset,
            "data": data_offset,
            "data_end": data_offset + compressed_size,
            "name_length": name_length,
        }
        cursor += 46 + name_length + extra_length + comment_length
    return records


def main() -> int:
    source_deck_data = (VALID / "basic-deck.json").read_bytes()
    deck = piyodeck_tool.decode_strict_json(source_deck_data, "basic-deck.json")
    assert isinstance(deck, dict)

    canonical_deck_data = piyodeck_tool.canonical_json(deck)
    canonical_manifest = _manifest(deck, canonical_deck_data)
    canonical_package = _package(canonical_manifest, canonical_deck_data)

    reviewable_manifest_data = (VALID / "basic-manifest.json").read_bytes()
    reviewable_manifest = piyodeck_tool.decode_strict_json(
        reviewable_manifest_data,
        "basic-manifest.json",
    )
    assert isinstance(reviewable_manifest, dict)
    pretty_package = piyodeck_tool.build_deterministic_zip(
        (
            ("manifest.json", reviewable_manifest_data),
            ("deck.json", source_deck_data),
        )
    )

    wrong_sha_manifest = copy.deepcopy(canonical_manifest)
    descriptor = wrong_sha_manifest["deck"]
    assert isinstance(descriptor, dict)
    descriptor["sha256"] = "0" * 64
    wrong_sha_package = _package(wrong_sha_manifest, canonical_deck_data)

    encoded_name = piyodeck_tool.canonical_json(deck["name"])
    unpaired_surrogate_deck = canonical_deck_data.replace(encoded_name, br'"\ud800"', 1)
    if unpaired_surrogate_deck == canonical_deck_data:
        raise RuntimeError("cannot create unpaired-surrogate fixture")
    unpaired_surrogate_package = _package(
        _manifest(deck, unpaired_surrogate_deck),
        unpaired_surrogate_deck,
    )

    records = _zip_records(canonical_package)
    manifest_record = records["manifest.json"]
    deck_record = records["deck.json"]

    encrypted = bytearray(canonical_package)
    _set_u16(encrypted, manifest_record["local"] + 6, 0x0801)
    _set_u16(encrypted, manifest_record["central"] + 8, 0x0801)

    data_descriptor = bytearray(canonical_package)
    _set_u16(data_descriptor, manifest_record["local"] + 6, 0x0808)
    _set_u16(data_descriptor, manifest_record["central"] + 8, 0x0808)

    deflate_method = bytearray(canonical_package)
    _set_u16(deflate_method, manifest_record["local"] + 8, 8)
    _set_u16(deflate_method, manifest_record["central"] + 10, 8)

    zip64 = bytearray(canonical_package)
    _set_u32(zip64, manifest_record["central"] + 20, 0xFFFFFFFF)

    header_mismatch = bytearray(canonical_package)
    local_crc = struct.unpack_from("<I", header_mismatch, manifest_record["local"] + 14)[0]
    _set_u32(header_mismatch, manifest_record["local"] + 14, local_crc ^ 1)

    crc_mismatch = bytearray(canonical_package)
    target_offset = canonical_package.find(
        b"user_",
        deck_record["data"],
        deck_record["data_end"],
    )
    if target_offset < 0:
        raise RuntimeError("cannot locate deterministic CRC mutation target")
    crc_mismatch[target_offset] = ord("v")

    unsafe_path = bytearray(canonical_package)
    unsafe_name = b"../a.json"
    if len(unsafe_name) != deck_record["name_length"]:
        raise RuntimeError("unsafe path mutation must preserve entry-name length")
    local_name_offset = deck_record["local"] + 30
    central_name_offset = deck_record["central"] + 46
    unsafe_path[local_name_offset : local_name_offset + len(unsafe_name)] = unsafe_name
    unsafe_path[central_name_offset : central_name_offset + len(unsafe_name)] = unsafe_name

    bom_deck_data = b"\xef\xbb\xbf" + canonical_deck_data
    bom_package = _package(_manifest(deck, bom_deck_data), bom_deck_data)

    duplicate_key_deck = b'{"version":1,' + canonical_deck_data[1:]
    duplicate_key_package = _package(
        _manifest(deck, duplicate_key_deck),
        duplicate_key_deck,
    )

    future_manifest = copy.deepcopy(canonical_manifest)
    future_manifest["format_version"] = 2
    future_version_package = _package(future_manifest, canonical_deck_data)

    case_specs = [
        (
            "canonical-basic",
            VALID / "basic.typedeck",
            canonical_package,
            True,
            "accepted_canonical",
        ),
        (
            "pretty-basic",
            VALID / "pretty-basic.typedeck",
            pretty_package,
            True,
            "accepted_noncanonical",
        ),
        ("wrong-sha", INVALID / "wrong-sha.typedeck", wrong_sha_package, False, "sha256_mismatch"),
        (
            "unpaired-surrogate",
            INVALID / "unpaired-surrogate.typedeck",
            unpaired_surrogate_package,
            False,
            "invalid_json",
        ),
        ("bom-deck", INVALID / "bom-deck.typedeck", bom_package, False, "invalid_json"),
        (
            "duplicate-key",
            INVALID / "duplicate-key.typedeck",
            duplicate_key_package,
            False,
            "invalid_json",
        ),
        (
            "future-version",
            INVALID / "future-version.typedeck",
            future_version_package,
            False,
            "unsupported_version",
        ),
        (
            "encrypted",
            INVALID / "encrypted.typedeck",
            bytes(encrypted),
            False,
            "unsupported_archive_feature",
        ),
        (
            "data-descriptor",
            INVALID / "data-descriptor.typedeck",
            bytes(data_descriptor),
            False,
            "unsupported_archive_feature",
        ),
        (
            "deflate-method",
            INVALID / "deflate-method.typedeck",
            bytes(deflate_method),
            False,
            "unsupported_archive_feature",
        ),
        ("zip64", INVALID / "zip64.typedeck", bytes(zip64), False, "unsupported_archive_feature"),
        (
            "header-mismatch",
            INVALID / "header-mismatch.typedeck",
            bytes(header_mismatch),
            False,
            "malformed_archive",
        ),
        (
            "crc-mismatch",
            INVALID / "crc-mismatch.typedeck",
            bytes(crc_mismatch),
            False,
            "crc_mismatch",
        ),
        (
            "unsafe-path",
            INVALID / "unsafe-path.typedeck",
            bytes(unsafe_path),
            False,
            "unsafe_entry_path",
        ),
        (
            "hidden-preamble",
            INVALID / "hidden-preamble.typedeck",
            b"hidden" + canonical_package,
            False,
            "malformed_archive",
        ),
    ]
    for _, path, data, _, _ in case_specs:
        _write(path, data)

    cases = {
        "schema_version": 1,
        "cases": [
            _case(identifier, path, data, valid, expectation)
            for identifier, path, data, valid, expectation in case_specs
        ],
    }
    _write(
        FIXTURES / "cases.json",
        (json.dumps(cases, ensure_ascii=False, indent=2) + "\n").encode("utf-8"),
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
