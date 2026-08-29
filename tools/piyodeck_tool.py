#!/usr/bin/env python3
"""Pack, inspect, and validate PIYOKEY ``.typedeck`` documents.

The implementation intentionally uses only the Python standard library.  It
does not delegate archive parsing to ``zipfile`` because PIYOKEY's document
format rejects otherwise-valid ZIP features such as compression, data
descriptors, extra fields, comments, preambles, and trailing bytes.
"""

from __future__ import annotations

import argparse
import binascii
import hashlib
import json
import os
import re
import stat
import struct
import sys
import tempfile
import unicodedata
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Iterable, Sequence


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DECK_SCHEMA = ROOT / "shared/schema/deck.schema.json"
MANIFEST_SCHEMA = ROOT / "shared/schema/piyodeck-manifest-v1.schema.json"

FORMAT_IDENTIFIER = "piyokey.deck-package"
FORMAT_VERSION = 1
DECK_SCHEMA_VERSION = 2
SUPPORTED_DECK_SCHEMA_VERSIONS = frozenset({1, 2})
ENTRY_NAMES = ("manifest.json", "deck.json")

MAX_PACKAGE_BYTES = 8 * 1024 * 1024
MAX_MANIFEST_BYTES = 16 * 1024
MAX_DECK_BYTES = 4 * 1024 * 1024
MAX_ITEMS = 1000

LOCAL_FILE_HEADER_SIGNATURE = 0x04034B50
CENTRAL_DIRECTORY_SIGNATURE = 0x02014B50
END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054B50
UTF8_FLAG = 0x0800
STORE_METHOD = 0
ZIP_VERSION = 20
DOS_TIME = 0
DOS_DATE = 0x0021  # 1980-01-01

USER_DECK_ID = re.compile(r"^user_[0-9a-f]{32}$")
USER_ITEM_ID = re.compile(r"^item_[0-9a-f]{32}$")
GENERIC_ID = re.compile(r"^[a-z0-9][a-z0-9_-]{2,63}$")

_LANGUAGE_TAG = re.compile(
    r"^(?:(?P<language>[A-Za-z]{2,3})(?P<extlangs>(?:-[A-Za-z]{3}){0,3})"
    r"|(?P<language4>[A-Za-z]{4})|(?P<language_long>[A-Za-z]{5,8}))"
    r"(?P<script>-[A-Za-z]{4})?(?P<region>-(?:[A-Za-z]{2}|[0-9]{3}))?"
    r"(?P<variants>(?:-(?:[0-9][A-Za-z0-9]{3}|[A-Za-z0-9]{5,8}))*)"
    r"(?P<extensions>(?:-[0-9A-WY-Za-wy-z](?:-[A-Za-z0-9]{2,8})+)*)"
    r"(?P<private>-x(?:-[A-Za-z0-9]{1,8})+)?$"
)
_PRIVATE_LANGUAGE_TAG = re.compile(r"^x(?:-[A-Za-z0-9]{1,8})+$", re.IGNORECASE)


class PiyoDeckToolError(Exception):
    """A user-facing validation or command failure."""


class DuplicateJSONKeyError(PiyoDeckToolError):
    def __init__(self, key: str):
        super().__init__(f"duplicate JSON object key: {key!r}")


@dataclass(frozen=True)
class CentralEntry:
    name: str
    name_bytes: bytes
    version_needed: int
    flags: int
    method: int
    modified_time: int
    modified_date: int
    crc32: int
    compressed_size: int
    uncompressed_size: int
    local_header_offset: int


@dataclass(frozen=True)
class ValidatedPackage:
    package_size: int
    manifest: dict[str, Any]
    deck: dict[str, Any]
    entries: dict[str, bytes]


def _strict_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise DuplicateJSONKeyError(key)
        result[key] = value
    return result


def _reject_json_constant(value: str) -> None:
    raise PiyoDeckToolError(f"non-finite JSON number is forbidden: {value}")


def _check_json_nesting(text: str, label: str) -> None:
    """Reject JSON nesting deeper than DeckKit's 64-level safety limit."""

    depth = 0
    in_string = False
    escaped = False
    for character in text:
        if in_string:
            if escaped:
                escaped = False
            elif character == "\\":
                escaped = True
            elif character == '"':
                in_string = False
            continue
        if character == '"':
            in_string = True
        elif character in "[{":
            depth += 1
            if depth > 64:
                raise PiyoDeckToolError(f"{label} nesting exceeds 64 levels")
        elif character in "]}":
            depth -= 1
            if depth < 0:
                raise PiyoDeckToolError(f"{label} has malformed JSON structure")


def _validate_unicode_scalars(value: Any, label: str, path: str = "$") -> None:
    """Reject decoded strings containing isolated UTF-16 surrogate code points."""

    if isinstance(value, str):
        try:
            value.encode("utf-8", errors="strict")
        except UnicodeEncodeError as error:
            raise PiyoDeckToolError(
                f"{label} contains invalid Unicode at {path}: {error}"
            ) from error
        return
    if isinstance(value, list):
        for index, item in enumerate(value):
            _validate_unicode_scalars(item, label, f"{path}[{index}]")
        return
    if isinstance(value, dict):
        for key, item in value.items():
            _validate_unicode_scalars(key, label, f"{path}.<key>")
            _validate_unicode_scalars(item, label, f"{path}.{key}")


def decode_strict_json(data: bytes, label: str) -> Any:
    if data.startswith(b"\xef\xbb\xbf"):
        raise PiyoDeckToolError(f"{label} must be UTF-8 without a BOM")
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError as error:
        raise PiyoDeckToolError(f"{label} is not valid UTF-8: {error}") from error
    _check_json_nesting(text, label)
    try:
        value = json.loads(
            text,
            object_pairs_hook=_strict_object,
            parse_constant=_reject_json_constant,
        )
        _validate_unicode_scalars(value, label)
        return value
    except DuplicateJSONKeyError:
        raise
    except (json.JSONDecodeError, RecursionError) as error:
        raise PiyoDeckToolError(f"{label} is not valid JSON: {error}") from error


def canonical_json(value: Any) -> bytes:
    """Return compact, stable UTF-8 JSON without a BOM or line breaks."""

    try:
        text = json.dumps(
            value,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
            allow_nan=False,
        )
    except (TypeError, ValueError) as error:
        raise PiyoDeckToolError(f"value cannot be encoded as PIYOKEY JSON: {error}") from error
    return text.encode("utf-8")


def _read_bounded(path: Path, maximum: int, label: str) -> bytes:
    try:
        size = path.stat().st_size
    except OSError as error:
        raise PiyoDeckToolError(f"cannot inspect {label} {path}: {error}") from error
    if size > maximum:
        raise PiyoDeckToolError(f"{label} is {size} bytes; maximum is {maximum}")
    try:
        data = path.read_bytes()
    except OSError as error:
        raise PiyoDeckToolError(f"cannot read {label} {path}: {error}") from error
    if len(data) != size:
        raise PiyoDeckToolError(f"{label} changed while it was being read: {path}")
    return data


def _unpack_from(fmt: str, data: bytes, offset: int, label: str) -> tuple[Any, ...]:
    size = struct.calcsize(fmt)
    if offset < 0 or offset + size > len(data):
        raise PiyoDeckToolError(f"malformed ZIP: truncated {label}")
    return struct.unpack_from(fmt, data, offset)


def _safe_entry_name(raw: bytes) -> str:
    try:
        name = raw.decode("utf-8")
    except UnicodeDecodeError as error:
        raise PiyoDeckToolError("unsafe ZIP entry path: invalid UTF-8") from error
    try:
        if name.encode("ascii") != raw:
            raise UnicodeEncodeError("ascii", name, 0, len(name), "non-ASCII path")
    except UnicodeEncodeError as error:
        raise PiyoDeckToolError(f"unsafe ZIP entry path: {name!r}") from error
    if (
        not name
        or name in {".", ".."}
        or "/" in name
        or "\\" in name
        or ":" in name
        or "\x00" in name
    ):
        raise PiyoDeckToolError(f"unsafe ZIP entry path: {name!r}")
    return name


def read_strict_zip(data: bytes) -> dict[str, bytes]:
    if len(data) > MAX_PACKAGE_BYTES:
        raise PiyoDeckToolError(
            f"package is {len(data)} bytes; maximum is {MAX_PACKAGE_BYTES}"
        )
    eocd_size = 22
    if len(data) < eocd_size:
        raise PiyoDeckToolError("malformed ZIP: missing end-of-central-directory record")
    eocd_offset = len(data) - eocd_size
    (
        signature,
        disk_number,
        central_disk,
        entries_on_disk,
        total_entries,
        central_size,
        central_offset,
        archive_comment_length,
    ) = _unpack_from("<IHHHHIIH", data, eocd_offset, "end-of-central-directory record")
    if signature != END_OF_CENTRAL_DIRECTORY_SIGNATURE:
        raise PiyoDeckToolError(
            "malformed ZIP: archive comment, trailing data, or missing end record"
        )
    if disk_number != 0 or central_disk != 0 or entries_on_disk != total_entries:
        raise PiyoDeckToolError("unsupported ZIP feature: split archive")
    if archive_comment_length != 0:
        raise PiyoDeckToolError("unsupported ZIP feature: archive comment")
    if total_entries != 2:
        raise PiyoDeckToolError(
            f"invalid entry set: expected {ENTRY_NAMES}, found {total_entries} entries"
        )
    if central_offset + central_size != eocd_offset:
        raise PiyoDeckToolError("malformed ZIP: invalid central-directory bounds")

    records: list[CentralEntry] = []
    cursor = central_offset
    central_end = central_offset + central_size
    for _ in range(total_entries):
        (
            central_signature,
            version_made_by,
            version_needed,
            flags,
            method,
            modified_time,
            modified_date,
            crc32,
            compressed_size,
            uncompressed_size,
            name_length,
            extra_length,
            comment_length,
            disk_start,
            _internal_attributes,
            external_attributes,
            local_header_offset,
        ) = _unpack_from("<IHHHHHHIIIHHHHHII", data, cursor, "central-directory entry")
        if central_signature != CENTRAL_DIRECTORY_SIGNATURE:
            raise PiyoDeckToolError("malformed ZIP: invalid central-directory entry")
        if version_needed > ZIP_VERSION:
            raise PiyoDeckToolError(f"unsupported ZIP version: {version_needed}")
        if 0xFFFFFFFF in {compressed_size, uncompressed_size, local_header_offset}:
            raise PiyoDeckToolError("unsupported ZIP feature: ZIP64")
        if flags & ~UTF8_FLAG:
            feature = "encryption" if flags & 0x0001 else f"general-purpose flag 0x{flags:04x}"
            raise PiyoDeckToolError(f"unsupported ZIP feature: {feature}")
        if method != STORE_METHOD:
            raise PiyoDeckToolError(f"unsupported ZIP compression method: {method}")
        if compressed_size != uncompressed_size:
            raise PiyoDeckToolError("malformed ZIP: STORE entry sizes differ")
        if extra_length:
            raise PiyoDeckToolError("unsupported ZIP feature: entry extra field")
        if comment_length:
            raise PiyoDeckToolError("unsupported ZIP feature: entry comment")
        if disk_start:
            raise PiyoDeckToolError("unsupported ZIP feature: split archive entry")

        host_system = version_made_by >> 8
        unix_type = (external_attributes >> 16) & 0xF000
        if host_system in {3, 19} and unix_type == stat.S_IFLNK:
            raise PiyoDeckToolError("unsupported ZIP feature: symbolic link")
        if host_system in {3, 19} and unix_type == stat.S_IFDIR:
            raise PiyoDeckToolError("unsupported ZIP feature: directory entry")
        if host_system in {3, 19} and unix_type not in {0, stat.S_IFREG}:
            raise PiyoDeckToolError("unsupported ZIP feature: non-regular entry")
        if external_attributes & 0x10:
            raise PiyoDeckToolError("unsupported ZIP feature: directory entry")

        name_start = cursor + 46
        name_end = name_start + name_length
        if name_end > central_end:
            raise PiyoDeckToolError("malformed ZIP: central entry exceeds directory bounds")
        name_bytes = data[name_start:name_end]
        name = _safe_entry_name(name_bytes)
        records.append(
            CentralEntry(
                name=name,
                name_bytes=name_bytes,
                version_needed=version_needed,
                flags=flags,
                method=method,
                modified_time=modified_time,
                modified_date=modified_date,
                crc32=crc32,
                compressed_size=compressed_size,
                uncompressed_size=uncompressed_size,
                local_header_offset=local_header_offset,
            )
        )
        cursor = name_end
    if cursor != central_end:
        raise PiyoDeckToolError("malformed ZIP: unused central-directory bytes")

    names = [record.name for record in records]
    if len(set(names)) != 2 or set(names) != set(ENTRY_NAMES):
        raise PiyoDeckToolError(f"invalid entry set: {names}")

    payloads: dict[str, bytes] = {}
    occupied: list[tuple[int, int]] = []
    for record in records:
        maximum = MAX_MANIFEST_BYTES if record.name == "manifest.json" else MAX_DECK_BYTES
        if record.uncompressed_size > maximum:
            raise PiyoDeckToolError(
                f"{record.name} is {record.uncompressed_size} bytes; maximum is {maximum}"
            )
        (
            local_signature,
            local_version_needed,
            local_flags,
            local_method,
            _local_time,
            _local_date,
            local_crc32,
            local_compressed_size,
            local_uncompressed_size,
            local_name_length,
            local_extra_length,
        ) = _unpack_from(
            "<IHHHHHIIIHH", data, record.local_header_offset, f"local header for {record.name}"
        )
        if local_signature != LOCAL_FILE_HEADER_SIGNATURE:
            raise PiyoDeckToolError(f"malformed ZIP: missing local header for {record.name}")
        local_fields = (
            local_version_needed,
            local_flags,
            local_method,
            local_crc32,
            local_compressed_size,
            local_uncompressed_size,
        )
        central_fields = (
            record.version_needed,
            record.flags,
            record.method,
            record.crc32,
            record.compressed_size,
            record.uncompressed_size,
        )
        if local_fields != central_fields:
            raise PiyoDeckToolError(
                f"malformed ZIP: local and central headers differ for {record.name}"
            )
        if local_extra_length:
            raise PiyoDeckToolError("unsupported ZIP feature: local entry extra field")
        name_start = record.local_header_offset + 30
        name_end = name_start + local_name_length
        data_end = name_end + record.compressed_size
        if data_end > central_offset:
            raise PiyoDeckToolError(f"malformed ZIP: {record.name} exceeds local-data bounds")
        if data[name_start:name_end] != record.name_bytes:
            raise PiyoDeckToolError(
                f"malformed ZIP: local and central paths differ for {record.name}"
            )
        payload = data[name_end:data_end]
        payloads[record.name] = payload
        occupied.append((record.local_header_offset, data_end))

    expected_offset = 0
    for start, end in sorted(occupied):
        if start != expected_offset or end < start:
            raise PiyoDeckToolError(
                "malformed ZIP: overlapping entries, hidden bytes, or archive preamble"
            )
        expected_offset = end
    if expected_offset != central_offset:
        raise PiyoDeckToolError("malformed ZIP: hidden bytes before central directory")
    for record in records:
        payload = payloads[record.name]
        if (binascii.crc32(payload) & 0xFFFFFFFF) != record.crc32:
            raise PiyoDeckToolError(f"ZIP CRC-32 check failed for {record.name}")
    return payloads


def build_deterministic_zip(entries: Sequence[tuple[str, bytes]]) -> bytes:
    names = [name for name, _ in entries]
    if names != list(ENTRY_NAMES):
        raise PiyoDeckToolError(f"writer entry order must be {ENTRY_NAMES}, found {names}")

    result = bytearray()
    central_records: list[tuple[bytes, int, int, int]] = []
    for name, payload in entries:
        name_bytes = name.encode("ascii")
        _safe_entry_name(name_bytes)
        crc32 = binascii.crc32(payload) & 0xFFFFFFFF
        local_offset = len(result)
        result.extend(
            struct.pack(
                "<IHHHHHIIIHH",
                LOCAL_FILE_HEADER_SIGNATURE,
                ZIP_VERSION,
                UTF8_FLAG,
                STORE_METHOD,
                DOS_TIME,
                DOS_DATE,
                crc32,
                len(payload),
                len(payload),
                len(name_bytes),
                0,
            )
        )
        result.extend(name_bytes)
        result.extend(payload)
        central_records.append((name_bytes, crc32, len(payload), local_offset))

    central_offset = len(result)
    for name_bytes, crc32, size, local_offset in central_records:
        result.extend(
            struct.pack(
                "<IHHHHHHIIIHHHHHII",
                CENTRAL_DIRECTORY_SIGNATURE,
                ZIP_VERSION,
                ZIP_VERSION,
                UTF8_FLAG,
                STORE_METHOD,
                DOS_TIME,
                DOS_DATE,
                crc32,
                size,
                size,
                len(name_bytes),
                0,
                0,
                0,
                0,
                0,
                local_offset,
            )
        )
        result.extend(name_bytes)
    central_size = len(result) - central_offset
    result.extend(
        struct.pack(
            "<IHHHHIIH",
            END_OF_CENTRAL_DIRECTORY_SIGNATURE,
            0,
            0,
            len(entries),
            len(entries),
            central_size,
            central_offset,
            0,
        )
    )
    package = bytes(result)
    if len(package) > MAX_PACKAGE_BYTES:
        raise PiyoDeckToolError(
            f"package is {len(package)} bytes; maximum is {MAX_PACKAGE_BYTES}"
        )
    return package


def _json_equal(left: Any, right: Any) -> bool:
    if type(left) is not type(right):
        return False
    return json.dumps(left, sort_keys=True, ensure_ascii=False) == json.dumps(
        right, sort_keys=True, ensure_ascii=False
    )


def _schema_type_matches(value: Any, expected: str) -> bool:
    if expected == "object":
        return isinstance(value, dict)
    if expected == "array":
        return isinstance(value, list)
    if expected == "string":
        return isinstance(value, str)
    if expected == "boolean":
        return isinstance(value, bool)
    if expected == "null":
        return value is None
    if expected == "integer":
        return isinstance(value, int) and not isinstance(value, bool)
    if expected == "number":
        return isinstance(value, (int, float)) and not isinstance(value, bool)
    return False


def _resolve_reference(reference: str, root_schema: dict[str, Any]) -> dict[str, Any]:
    if not reference.startswith("#/"):
        raise PiyoDeckToolError(f"unsupported non-local JSON Schema reference: {reference}")
    current: Any = root_schema
    for component in reference[2:].split("/"):
        component = component.replace("~1", "/").replace("~0", "~")
        if not isinstance(current, dict) or component not in current:
            raise PiyoDeckToolError(f"invalid JSON Schema reference: {reference}")
        current = current[component]
    if not isinstance(current, dict):
        raise PiyoDeckToolError(f"JSON Schema reference is not an object: {reference}")
    return current


def _format_matches(value: str, format_name: str) -> bool:
    if format_name == "date-time":
        try:
            parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        except ValueError:
            return False
        return parsed.tzinfo is not None
    if format_name == "uri-reference":
        return "\x00" not in value
    return True


def schema_issues(instance: Any, schema: dict[str, Any]) -> list[str]:
    issues: list[str] = []

    def visit(value: Any, node: dict[str, Any], path: str) -> None:
        if "$ref" in node:
            visit(value, _resolve_reference(node["$ref"], schema), path)
            return
        alternatives = node.get("oneOf")
        if isinstance(alternatives, list):
            successes = 0
            for alternative in alternatives:
                alternative_issues: list[str] = []
                before = len(issues)
                visit(value, alternative, path)
                alternative_issues.extend(issues[before:])
                del issues[before:]
                if not alternative_issues:
                    successes += 1
            if successes != 1:
                issues.append(f"{path}: must match exactly one oneOf alternative")
            return

        expected_type = node.get("type")
        if isinstance(expected_type, str) and not _schema_type_matches(value, expected_type):
            issues.append(f"{path}: expected {expected_type}")
            return
        enum_values = node.get("enum")
        if isinstance(enum_values, list) and not any(
            _json_equal(value, candidate) for candidate in enum_values
        ):
            issues.append(f"{path}: value is not in enum")

        if isinstance(value, dict):
            properties = node.get("properties", {})
            required = node.get("required", [])
            for key in required:
                if key not in value:
                    issues.append(f"{path}.{key}: required property is missing")
            additional_properties = node.get("additionalProperties")
            if additional_properties is False:
                for key in value:
                    if key not in properties:
                        issues.append(f"{path}.{key}: additional property is forbidden")
            property_names = node.get("propertyNames")
            if isinstance(property_names, dict):
                for key in value:
                    visit(key, property_names, f"{path}.<key:{key}>")
            minimum_properties = node.get("minProperties")
            if isinstance(minimum_properties, int) and len(value) < minimum_properties:
                issues.append(f"{path}: expected at least {minimum_properties} properties")
            for key, child in value.items():
                child_schema = properties.get(key)
                if isinstance(child_schema, dict):
                    visit(child, child_schema, f"{path}.{key}")
                elif isinstance(additional_properties, dict):
                    visit(child, additional_properties, f"{path}.{key}")

        if isinstance(value, list):
            minimum = node.get("minItems")
            maximum = node.get("maxItems")
            if isinstance(minimum, int) and len(value) < minimum:
                issues.append(f"{path}: expected at least {minimum} items")
            if isinstance(maximum, int) and len(value) > maximum:
                issues.append(f"{path}: expected at most {maximum} items")
            if node.get("uniqueItems") is True:
                seen: set[str] = set()
                for item in value:
                    key = json.dumps(item, ensure_ascii=False, sort_keys=True)
                    if key in seen:
                        issues.append(f"{path}: duplicate array item")
                        break
                    seen.add(key)
            item_schema = node.get("items")
            if isinstance(item_schema, dict):
                for index, child in enumerate(value):
                    visit(child, item_schema, f"{path}[{index}]")

        if isinstance(value, str):
            minimum = node.get("minLength")
            maximum = node.get("maxLength")
            if isinstance(minimum, int) and len(value) < minimum:
                issues.append(f"{path}: expected length >= {minimum}")
            if isinstance(maximum, int) and len(value) > maximum:
                issues.append(f"{path}: expected length <= {maximum}")
            pattern = node.get("pattern")
            if isinstance(pattern, str) and re.search(pattern, value) is None:
                issues.append(f"{path}: does not match {pattern}")
            format_name = node.get("format")
            if isinstance(format_name, str) and not _format_matches(value, format_name):
                issues.append(f"{path}: invalid {format_name}")

        if isinstance(value, (int, float)) and not isinstance(value, bool):
            minimum = node.get("minimum")
            maximum = node.get("maximum")
            if isinstance(minimum, (int, float)) and value < minimum:
                issues.append(f"{path}: value is below minimum {minimum}")
            if isinstance(maximum, (int, float)) and value > maximum:
                issues.append(f"{path}: value exceeds maximum {maximum}")

    visit(instance, schema, "$")
    return issues


def _load_schema(path: Path) -> dict[str, Any]:
    raw = _read_bounded(path, 1024 * 1024, "JSON Schema")
    value = decode_strict_json(raw, str(path))
    if not isinstance(value, dict):
        raise PiyoDeckToolError(f"JSON Schema root must be an object: {path}")
    return value


def _trimmed(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())


def canonical_language_tag(value: str) -> str | None:
    """Return structural BCP 47 canonical casing, or ``None`` if malformed."""

    if not isinstance(value, str) or not 2 <= len(value) <= 63 or "_" in value:
        return None
    if _PRIVATE_LANGUAGE_TAG.fullmatch(value):
        return "-".join(part.lower() for part in value.split("-"))
    match = _LANGUAGE_TAG.fullmatch(value)
    if match is None:
        return None
    parts = value.split("-")
    output: list[str] = [parts[0].lower()]
    index = 1
    language_length = len(parts[0])
    if language_length in (2, 3):
        extlang_count = 0
        while index < len(parts) and len(parts[index]) == 3 and parts[index].isalpha() and extlang_count < 3:
            output.append(parts[index].lower())
            index += 1
            extlang_count += 1
    if index < len(parts) and len(parts[index]) == 4 and parts[index].isalpha():
        output.append(parts[index].title())
        index += 1
    if index < len(parts) and (
        (len(parts[index]) == 2 and parts[index].isalpha())
        or (len(parts[index]) == 3 and parts[index].isdigit())
    ):
        output.append(parts[index].upper() if parts[index].isalpha() else parts[index])
        index += 1
    output.extend(part.lower() for part in parts[index:])
    return "-".join(output)


def _locale_key_issues(localizations: Any, path: str) -> list[str]:
    if not isinstance(localizations, dict):
        return []
    issues: list[str] = []
    canonical_keys: set[str] = set()
    for key in localizations:
        canonical = canonical_language_tag(key)
        if canonical is None:
            issues.append(f"{path}.{key}: malformed BCP 47 language tag")
        elif canonical != key:
            issues.append(f"{path}.{key}: language tag must use canonical form {canonical}")
        elif canonical in canonical_keys:
            issues.append(f"{path}.{key}: duplicate canonical language tag")
        canonical_keys.add(canonical or key)
    return issues


def locale_lookup_candidates(requested: str, default_locale: str | None) -> list[str]:
    """Return localization keys in the normative display fallback order."""

    normalized_requested = canonical_language_tag(requested.replace("_", "-"))
    candidates: list[str] = []
    if normalized_requested:
        candidates.append(normalized_requested)
        base = normalized_requested.split("-", 1)[0]
        if base != normalized_requested:
            candidates.append(base)
    if default_locale:
        candidates.append(default_locale)
    candidates.append("en")
    return list(dict.fromkeys(candidates))


def localized_deck_value(deck: dict[str, Any], requested: str, field: str) -> Any:
    """Resolve localized deck metadata, ending at the legacy Japanese base field."""

    localizations = deck.get("localizations")
    if isinstance(localizations, dict):
        for candidate in locale_lookup_candidates(requested, deck.get("default_locale")):
            localization = localizations.get(candidate)
            if isinstance(localization, dict) and field in localization:
                return localization[field]
    if field == "author_nickname":
        author = deck.get("author")
        return author.get("nickname") if isinstance(author, dict) else None
    return deck.get(field)


def localized_item_value(
    deck: dict[str, Any], item: dict[str, Any], requested: str, field: str
) -> Any:
    """Resolve an item clue, ending at the legacy ``*_ja`` base field."""

    localizations = item.get("localizations")
    if isinstance(localizations, dict):
        for candidate in locale_lookup_candidates(requested, deck.get("default_locale")):
            localization = localizations.get(candidate)
            if isinstance(localization, dict) and field in localization:
                return localization[field]
    return item.get(f"{field}_ja")


def _parse_datetime(value: Any) -> datetime | None:
    if not isinstance(value, str):
        return None
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None
    return parsed if parsed.tzinfo is not None else None


_HANGUL_JAMO = set(
    "ㄱㄲㄳㄴㄵㄶㄷㄸㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅃㅄㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
)
_ALLOWED_LITERAL_PUNCTUATION = set(" .,!?…'\"()-·~♡♥。！？")


def _is_hangul(character: str) -> bool:
    return character in _HANGUL_JAMO or "가" <= character <= "힣"


def _is_supported_target_character(character: str) -> bool:
    if _is_hangul(character) or character in _ALLOWED_LITERAL_PUNCTUATION:
        return True
    if character.isspace():
        return True
    try:
        unicodedata.numeric(character)
        return True
    except (TypeError, ValueError):
        return False


def deck_semantic_issues(deck: dict[str, Any], deck_schema_version: int | None = None) -> list[str]:
    issues: list[str] = []

    for path, value in (
        ("$.name", deck.get("name")),
        ("$.author.id", deck.get("author", {}).get("id") if isinstance(deck.get("author"), dict) else None),
        (
            "$.author.nickname",
            deck.get("author", {}).get("nickname") if isinstance(deck.get("author"), dict) else None,
        ),
    ):
        if not _trimmed(value):
            issues.append(f"{path}: must not be blank")

    author = deck.get("author")
    if isinstance(author, dict):
        author_id = author.get("id")
        if isinstance(author_id, str) and GENERIC_ID.fullmatch(author_id) is None:
            issues.append("$.author.id: invalid identifier")

    deck_id = deck.get("deck_id")
    if isinstance(deck_id, str) and GENERIC_ID.fullmatch(deck_id) is None:
        issues.append("$.deck_id: invalid identifier")

    tags = deck.get("tags")
    if isinstance(tags, list):
        for index, tag in enumerate(tags):
            if not _trimmed(tag):
                issues.append(f"$.tags[{index}]: must not be blank")

    created_at = _parse_datetime(deck.get("created_at"))
    updated_at = _parse_datetime(deck.get("updated_at"))
    if created_at is not None and updated_at is not None and updated_at < created_at:
        issues.append("$.updated_at: must not be earlier than created_at")

    localizations = deck.get("localizations")
    issues.extend(_locale_key_issues(localizations, "$.localizations"))
    default_locale = deck.get("default_locale")
    if deck_schema_version == 1:
        if default_locale is not None:
            issues.append("$.default_locale: deck schema v1 does not define this field")
        if isinstance(localizations, dict):
            for language in localizations:
                if language not in {"en", "ko"}:
                    issues.append(f"$.localizations.{language}: deck schema v1 supports only en and ko")
    elif deck_schema_version == 2:
        canonical_default = canonical_language_tag(default_locale) if isinstance(default_locale, str) else None
        if canonical_default is None:
            issues.append("$.default_locale: canonical BCP 47 language tag is required")
        elif canonical_default != default_locale:
            issues.append(f"$.default_locale: language tag must use canonical form {canonical_default}")
        if not isinstance(localizations, dict) or not localizations:
            issues.append("$.localizations: deck schema v2 requires at least one content locale")
        elif default_locale not in localizations:
            issues.append("$.default_locale: must name a key declared in localizations")
    if isinstance(localizations, dict):
        for language, localization in localizations.items():
            if not isinstance(localization, dict):
                continue
            for field in ("name", "author_nickname"):
                if not _trimmed(localization.get(field)):
                    issues.append(f"$.localizations.{language}.{field}: must not be blank")
            localized_tags = localization.get("tags")
            if isinstance(tags, list) and isinstance(localized_tags, list):
                if len(localized_tags) != len(tags):
                    issues.append(
                        f"$.localizations.{language}.tags: count must match base tags"
                    )
                for index, tag in enumerate(localized_tags):
                    if not _trimmed(tag):
                        issues.append(
                            f"$.localizations.{language}.tags[{index}]: must not be blank"
                        )

    items = deck.get("items")
    if not isinstance(items, list):
        return issues
    if not items:
        issues.append("$.items: at least one item is required")
    seen_ids: set[str] = set()
    catalog_required_locales = (
        set(localizations) - {"ko"}
        if deck_schema_version is None and isinstance(localizations, dict)
        else set()
    )
    english_metadata = isinstance(localizations, dict) and "en" in localizations
    declared_locales = set(localizations) if isinstance(localizations, dict) else set()
    for index, item in enumerate(items):
        if not isinstance(item, dict):
            continue
        path = f"$.items[{index}]"
        item_id = item.get("id")
        if isinstance(item_id, str):
            if item_id in seen_ids:
                issues.append(f"{path}.id: duplicate item identifier")
            seen_ids.add(item_id)
            if GENERIC_ID.fullmatch(item_id) is None:
                issues.append(f"{path}.id: invalid identifier")
        ko = item.get("ko")
        if isinstance(ko, str):
            if not ko.strip():
                issues.append(f"{path}.ko: must not be blank")
            if len(ko) > 10:
                issues.append(f"{path}.ko: must contain at most 10 characters")
            if ko and not any(_is_hangul(character) for character in ko):
                issues.append(f"{path}.ko: must contain Hangul")
            for character in ko:
                if not _is_supported_target_character(character):
                    issues.append(f"{path}.ko: unsupported character {character!r}")
                    break
        for field in ("reading_ja", "meaning_ja"):
            if not _trimmed(item.get(field)):
                issues.append(f"{path}.{field}: must not be blank")
        item_localizations = item.get("localizations")
        for language in sorted(catalog_required_locales):
            if not isinstance(item_localizations, dict) or language not in item_localizations:
                issues.append(f"{path}.localizations.{language}: required by deck metadata")
        issues.extend(_locale_key_issues(item_localizations, f"{path}.localizations"))
        if deck_schema_version == 1 and isinstance(item_localizations, dict):
            for language in item_localizations:
                if language not in {"en", "ko"}:
                    issues.append(f"{path}.localizations.{language}: deck schema v1 supports only en and ko")
        if deck_schema_version == 1 and english_metadata and (
            not isinstance(item_localizations, dict) or "en" not in item_localizations
        ):
            issues.append(f"{path}.localizations.en: required by English deck metadata")
        if deck_schema_version == 2:
            item_locales = set(item_localizations) if isinstance(item_localizations, dict) else set()
            for missing in sorted(declared_locales - item_locales):
                issues.append(f"{path}.localizations.{missing}: required by declared content locales")
            for extra in sorted(item_locales - declared_locales):
                issues.append(f"{path}.localizations.{extra}: locale is not declared by deck metadata")
        if isinstance(item_localizations, dict):
            for language, localization in item_localizations.items():
                if not isinstance(localization, dict):
                    continue
                for field in ("reading", "meaning"):
                    if not _trimmed(localization.get(field)):
                        issues.append(
                            f"{path}.localizations.{language}.{field}: must not be blank"
                        )
    return issues


def user_deck_issues(deck: dict[str, Any]) -> list[str]:
    issues: list[str] = []
    author = deck.get("author")
    if isinstance(author, dict):
        author_id = author.get("id")
        if isinstance(author_id, str) and author_id.startswith("official_"):
            issues.append("$.author.id: official_ is reserved")

    deck_id = deck.get("deck_id")
    if isinstance(deck_id, str):
        if deck_id.startswith("official_"):
            issues.append("$.deck_id: official_ is reserved")
        if USER_DECK_ID.fullmatch(deck_id) is None:
            issues.append("$.deck_id: expected user_ followed by 32 lower-case hex characters")
    if deck.get("official") is not False:
        issues.append("$.official: user deck must set official to false")

    items = deck.get("items")
    if not isinstance(items, list):
        return issues
    if not 1 <= len(items) <= MAX_ITEMS:
        issues.append(f"$.items: user decks require 1...{MAX_ITEMS} items")
    for index, item in enumerate(items):
        if not isinstance(item, dict):
            continue
        path = f"$.items[{index}]"
        item_id = item.get("id")
        if isinstance(item_id, str):
            if item_id.startswith("official_"):
                issues.append(f"{path}.id: official_ is reserved")
            if USER_ITEM_ID.fullmatch(item_id) is None:
                issues.append(
                    f"{path}.id: expected item_ followed by 32 lower-case hex characters"
                )
        if item.get("audio", object()) is not None:
            issues.append(f"{path}.audio: .typedeck v1 requires null")
    return issues


def _raise_issues(label: str, issues: Iterable[str]) -> None:
    materialized = list(issues)
    if not materialized:
        return
    shown = materialized[:12]
    suffix = f" (+{len(materialized) - len(shown)} more)" if len(materialized) > len(shown) else ""
    raise PiyoDeckToolError(f"{label}: " + "; ".join(shown) + suffix)


def _validate_manifest(manifest: Any) -> dict[str, Any]:
    if not isinstance(manifest, dict):
        raise PiyoDeckToolError("manifest.json root must be an object")
    format_version = manifest.get("format_version")
    deck_schema_version = manifest.get("deck_schema_version")
    if isinstance(format_version, int) and not isinstance(format_version, bool):
        if format_version != FORMAT_VERSION:
            raise PiyoDeckToolError(f"unsupported .typedeck format_version: {format_version}")
    if isinstance(deck_schema_version, int) and not isinstance(deck_schema_version, bool):
        if deck_schema_version not in SUPPORTED_DECK_SCHEMA_VERSIONS:
            raise PiyoDeckToolError(
                f"unsupported deck_schema_version: {deck_schema_version}"
            )
    schema = _load_schema(MANIFEST_SCHEMA)
    _raise_issues("manifest.json does not match v1 schema", schema_issues(manifest, schema))
    return manifest


def validate_package_data(data: bytes, deck_schema: dict[str, Any]) -> ValidatedPackage:
    entries = read_strict_zip(data)
    manifest_raw = entries["manifest.json"]
    deck_raw = entries["deck.json"]
    manifest_value = decode_strict_json(manifest_raw, "manifest.json")
    deck_value = decode_strict_json(deck_raw, "deck.json")
    manifest = _validate_manifest(manifest_value)

    descriptor = manifest["deck"]
    if descriptor["size_bytes"] != len(deck_raw):
        raise PiyoDeckToolError("manifest mismatch: deck.size_bytes")
    digest = hashlib.sha256(deck_raw).hexdigest()
    if descriptor["sha256"] != digest:
        raise PiyoDeckToolError("deck.json SHA-256 does not match manifest")

    if not isinstance(deck_value, dict):
        raise PiyoDeckToolError("deck.json root must be an object")

    for field, actual in (
        ("deck_id", deck_value.get("deck_id")),
        ("deck_version", deck_value.get("version")),
        (
            "item_count",
            len(deck_value.get("items"))
            if isinstance(deck_value.get("items"), list)
            else None,
        ),
    ):
        if type(descriptor[field]) is not type(actual) or descriptor[field] != actual:
            raise PiyoDeckToolError(f"manifest mismatch: deck.{field}")
    _raise_issues("deck.json does not match deck schema", schema_issues(deck_value, deck_schema))
    _raise_issues(
        "deck.json fails DeckKit semantics",
        deck_semantic_issues(deck_value, manifest["deck_schema_version"]),
    )
    _raise_issues("deck.json is not a valid user deck", user_deck_issues(deck_value))
    return ValidatedPackage(
        package_size=len(data),
        manifest=manifest,
        deck=deck_value,
        entries=entries,
    )


def validate_package(path: Path, schema_path: Path) -> ValidatedPackage:
    data = _read_bounded(path, MAX_PACKAGE_BYTES, ".typedeck package")
    return validate_package_data(data, _load_schema(schema_path))


def pack(deck_path: Path, output_path: Path) -> ValidatedPackage:
    if output_path.suffix.lower() != ".typedeck":
        raise PiyoDeckToolError("output file must use the .typedeck extension")
    deck_input = _read_bounded(deck_path, MAX_DECK_BYTES, "deck JSON")
    deck = decode_strict_json(deck_input, str(deck_path))
    if not isinstance(deck, dict):
        raise PiyoDeckToolError("deck JSON root must be an object")
    deck_schema = _load_schema(DEFAULT_DECK_SCHEMA)
    _raise_issues("deck JSON does not match deck schema", schema_issues(deck, deck_schema))
    output_schema_version = 2 if "default_locale" in deck else 1
    _raise_issues(
        "deck JSON fails DeckKit semantics",
        deck_semantic_issues(deck, output_schema_version),
    )
    _raise_issues("deck JSON is not a valid user deck", user_deck_issues(deck))

    deck_data = canonical_json(deck)
    if len(deck_data) > MAX_DECK_BYTES:
        raise PiyoDeckToolError(
            f"canonical deck.json is {len(deck_data)} bytes; maximum is {MAX_DECK_BYTES}"
        )
    manifest = {
        "format": FORMAT_IDENTIFIER,
        "format_version": FORMAT_VERSION,
        "deck_schema_version": output_schema_version,
        "deck": {
            "path": "deck.json",
            "media_type": "application/json",
            "deck_id": deck["deck_id"],
            "deck_version": deck["version"],
            "item_count": len(deck["items"]),
            "size_bytes": len(deck_data),
            "sha256": hashlib.sha256(deck_data).hexdigest(),
        },
    }
    manifest_data = canonical_json(manifest)
    if len(manifest_data) > MAX_MANIFEST_BYTES:
        raise PiyoDeckToolError(
            f"manifest.json is {len(manifest_data)} bytes; maximum is {MAX_MANIFEST_BYTES}"
        )
    package_data = build_deterministic_zip(
        (("manifest.json", manifest_data), ("deck.json", deck_data))
    )

    try:
        output_path.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile(
            prefix=f".{output_path.name}.", dir=output_path.parent, delete=False
        ) as temporary:
            temporary.write(package_data)
            temporary.flush()
            os.fsync(temporary.fileno())
            temporary_path = Path(temporary.name)
        os.replace(temporary_path, output_path)
    except OSError as error:
        try:
            temporary_path.unlink(missing_ok=True)
        except (NameError, OSError):
            pass
        raise PiyoDeckToolError(f"cannot write {output_path}: {error}") from error
    return validate_package_data(package_data, deck_schema)


def inspect_summary(path: Path, package: ValidatedPackage) -> dict[str, Any]:
    descriptor = package.manifest["deck"]
    return {
        "path": str(path),
        "package_size_bytes": package.package_size,
        "format": package.manifest["format"],
        "format_version": package.manifest["format_version"],
        "deck_schema_version": package.manifest["deck_schema_version"],
        "entries": [
            {"path": name, "size_bytes": len(package.entries[name])} for name in ENTRY_NAMES
        ],
        "deck": {
            "deck_id": descriptor["deck_id"],
            "deck_version": descriptor["deck_version"],
            "name": package.deck["name"],
            "item_count": descriptor["item_count"],
            "sha256": descriptor["sha256"],
        },
    }


def make_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Pack, inspect, and validate PIYOKEY .typedeck v1 documents."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    pack_parser = subparsers.add_parser("pack", help="create a deterministic .typedeck")
    pack_parser.add_argument("--deck", required=True, type=Path, help="source deck.json")
    pack_parser.add_argument("--output", required=True, type=Path, help="output .typedeck")

    inspect_parser = subparsers.add_parser("inspect", help="validate and summarize a package")
    inspect_parser.add_argument("file", type=Path, help="input .typedeck")

    validate_parser = subparsers.add_parser("validate", help="strictly validate a package")
    validate_parser.add_argument("file", type=Path, help="input .typedeck")
    validate_parser.add_argument(
        "--deck-schema", required=True, type=Path, help="deck.schema.json path"
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = make_parser().parse_args(argv)
    try:
        if args.command == "pack":
            package = pack(args.deck, args.output)
            descriptor = package.manifest["deck"]
            print(
                f"packed {args.output} ({package.package_size} bytes, "
                f"{descriptor['item_count']} items, deck version {descriptor['deck_version']})"
            )
        elif args.command == "inspect":
            package = validate_package(args.file, DEFAULT_DECK_SCHEMA)
            print(json.dumps(inspect_summary(args.file, package), ensure_ascii=False, indent=2))
        elif args.command == "validate":
            package = validate_package(args.file, args.deck_schema)
            descriptor = package.manifest["deck"]
            print(
                f"valid: {args.file} "
                f"({descriptor['deck_id']} v{descriptor['deck_version']}, "
                f"{descriptor['item_count']} items)"
            )
        else:  # pragma: no cover - argparse keeps this unreachable.
            raise PiyoDeckToolError(f"unknown command: {args.command}")
    except PiyoDeckToolError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
