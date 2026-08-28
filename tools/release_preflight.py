#!/usr/bin/env python3
"""Static mobile-store release checks for typee / ピヨキー.

The default mode verifies deterministic repository, bundle, and listing requirements.
Use --strict for iOS submission readiness, including manually recorded QA gates.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import plistlib
import re
import struct
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class Finding:
    severity: str
    message: str


REQUIRED_PRIVACY_REASONS = {
    "NSPrivacyAccessedAPICategoryUserDefaults": ["CA92.1"],
    "NSPrivacyAccessedAPICategoryActiveKeyboards": ["54BD.1"],
}
REQUIRED_COLLECTED_DATA = {
    "NSPrivacyCollectedDataTypeCrashData": {
        "NSPrivacyCollectedDataTypePurposeAppFunctionality",
        "NSPrivacyCollectedDataTypePurposeAnalytics",
    },
    "NSPrivacyCollectedDataTypeProductInteraction": {
        "NSPrivacyCollectedDataTypePurposeAnalytics",
    },
    "NSPrivacyCollectedDataTypeDeviceID": {
        "NSPrivacyCollectedDataTypePurposeAnalytics",
        "NSPrivacyCollectedDataTypePurposeAppFunctionality",
    },
    "NSPrivacyCollectedDataTypeOtherUsageData": {
        "NSPrivacyCollectedDataTypePurposeAnalytics",
    },
    "NSPrivacyCollectedDataTypeGameplayContent": {
        "NSPrivacyCollectedDataTypePurposeAnalytics",
    },
    "NSPrivacyCollectedDataTypePurchaseHistory": {
        "NSPrivacyCollectedDataTypePurposeAnalytics",
    },
    "NSPrivacyCollectedDataTypeOtherDiagnosticData": {
        "NSPrivacyCollectedDataTypePurposeAnalytics",
        "NSPrivacyCollectedDataTypePurposeAppFunctionality",
    },
}

EXPECTED_DISPLAY_NAMES = {
    "ja": "ピヨキー",
    "en": "typee",
}

DEFAULT_TYPING_SOUND_SHA256 = "0a2399f0434b35b58e25ae22f858e447f77d2c4429557312ac51deb8358a9427"
EXPECTED_PRONUNCIATION_PROMPT_COUNT = 581
GAME_CENTER_AVAILABILITY_KEY = "PiyokeyGameCenterAvailableLeaderboardIDs"
GAME_CENTER_INTENDED_KEY = "PiyokeyGameCenterIntendedLeaderboardIDs"
DECK_MAKER_PRODUCT_ID = "app.piyokey.deckmaker.lifetime"
DECK_MAKER_LOCALIZATIONS = {
    "ja": ("ピヨキー pro", "ユーザーデッキ無制限と作成・編集をずっと利用"),
    "en-US": ("typee pro", "Unlimited user decks, creation, and editing."),
    "ko": ("피요키 프로", "사용자 덱 무제한 보관과 생성·편집을 평생 이용"),
}
STOREKIT_LOCALE_MAP = {"ja": "ja", "en-US": "en_US", "ko": "ko"}

VERSION_1_1_APP_LOCALES = frozenset({"ja", "en"})
GLOBAL_APP_STORE_LOCALES = frozenset({"en-US", "en-GB", "en-AU", "en-CA", "ko", "ja"})
GLOBAL_APP_STORE_NAMES = {
    "ja": "韓国語タイピング - ピヨキー",
    "ko": "한글 타자 연습 - typee",
    "en-US": "Korean Typing - typee",
    "en-GB": "Korean Typing - typee",
    "en-AU": "Korean Typing - typee",
    "en-CA": "Korean Typing - typee",
}
ANDROID_M7_STATE = "resumed_separate_google_play_release_track"
ALL_COUNTRIES_SELECTION = "ALL_COUNTRIES_OR_REGIONS"
GOOGLE_PLAY_LOCALES = frozenset({"en-US", "ja", "ko"})
GOOGLE_PLAY_EXTERNAL_GATES = frozenset(
    {
        "play_console_app_created",
        "application_id_owned",
        "content_rights_approved",
        "upload_signing_ready",
        "deck_maker_product_ready",
        "play_games_resources_ready",
        "data_safety_completed",
        "content_rating_completed",
        "localized_screenshots_uploaded",
        "signed_candidate_device_qa_completed",
    }
)
GOOGLE_PLAY_CONSOLE_STATUS = "operator_review_required_not_applied"

IPHONE_SCREENSHOT_SIZES = {
    (1260, 2736),
    (1290, 2796),
    (1320, 2868),
    (1284, 2778),
    (1242, 2688),
}
IPHONE_SCREENSHOT_SIZES |= {(height, width) for width, height in IPHONE_SCREENSHOT_SIZES}


def load_plist(path: Path) -> dict[str, Any]:
    with path.open("rb") as handle:
        value = plistlib.load(handle)
    if not isinstance(value, dict):
        raise ValueError(f"{path} root must be a dictionary")
    return value


def pronunciation_audio_findings(root: Path) -> list[Finding]:
    findings: list[Finding] = []
    catalog_root = root / "shared/mock_catalog"
    prompt_manifest = catalog_root / "pronunciation_prompts.json"
    audio_root = catalog_root / "audio"
    expected: dict[str, str] = {}

    try:
        for deck_root in (catalog_root / "decks", catalog_root / "updates/decks"):
            for deck_path in sorted(deck_root.rglob("*.json")):
                deck = json.loads(deck_path.read_text(encoding="utf-8"))
                for item in deck["items"]:
                    korean = item["ko"]
                    digest = hashlib.sha256(korean.encode("utf-8")).hexdigest()[:20]
                    relative_path = f"audio/ko_{digest}.mp3"
                    add(
                        findings,
                        item.get("audio") == relative_path,
                        f"Deck pronunciation path is not canonical: {deck_path.name}:{item.get('id')}",
                    )
                    previous = expected.setdefault(relative_path, korean)
                    add(
                        findings,
                        previous == korean,
                        f"Pronunciation hash collision: {relative_path}",
                    )

        manifest = json.loads(prompt_manifest.read_text(encoding="utf-8"))
        add(
            findings,
            set(manifest) == {"schema_version", "prompts"} and manifest["schema_version"] == 1,
            "Pronunciation prompt manifest schema differs",
        )
        prompts = manifest.get("prompts", [])
        add(
            findings,
            isinstance(prompts, list)
            and bool(prompts)
            and all(isinstance(prompt, str) and prompt.strip() for prompt in prompts)
            and len(prompts) == len(set(prompts)),
            "Pronunciation prompt manifest contains invalid or duplicate prompts",
        )
        for korean in prompts if isinstance(prompts, list) else []:
            if not isinstance(korean, str):
                continue
            digest = hashlib.sha256(korean.encode("utf-8")).hexdigest()[:20]
            relative_path = f"audio/ko_{digest}.mp3"
            previous = expected.setdefault(relative_path, korean)
            add(findings, previous == korean, f"Pronunciation hash collision: {relative_path}")
    except (OSError, KeyError, TypeError, json.JSONDecodeError) as error:
        return [Finding("ERROR", f"Invalid pronunciation source data: {error}")]

    actual_mp3 = {
        path.relative_to(catalog_root).as_posix()
        for path in audio_root.glob("ko_*.mp3")
    }
    legacy_caf = sorted(path.name for path in audio_root.glob("ko_*.caf"))
    add(
        findings,
        len(expected) == EXPECTED_PRONUNCIATION_PROMPT_COUNT,
        f"Pronunciation prompt count differs: {len(expected)}",
    )
    add(
        findings,
        actual_mp3 == set(expected),
        "Bundled gTTS MP3 set differs from app-provided pronunciation prompts",
    )
    add(findings, not legacy_caf, f"Legacy CAF pronunciation assets remain: {legacy_caf[:5]}")
    invalid_files = [
        relative_path
        for relative_path in sorted(actual_mp3)
        if (catalog_root / relative_path).stat().st_size <= 1_024
    ]
    add(findings, not invalid_files, f"Bundled gTTS MP3 files are empty: {invalid_files[:5]}")
    return findings


def png_info(path: Path) -> tuple[int, int, bool]:
    with path.open("rb") as handle:
        header = handle.read(33)
    if len(header) < 33 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        raise ValueError(f"{path} is not a valid PNG")
    width, height, bit_depth, color_type = struct.unpack(">IIBB", header[16:26])
    del bit_depth
    has_alpha = color_type in {4, 6}
    return width, height, has_alpha


def parse_strings(path: Path) -> dict[str, str]:
    text = path.read_text(encoding="utf-8")
    pairs = re.findall(r'^\s*"((?:[^"\\]|\\.)*)"\s*=\s*"((?:[^"\\]|\\.)*)"\s*;', text, re.MULTILINE)
    return {key: value for key, value in pairs}


def add(findings: list[Finding], condition: bool, message: str, severity: str = "ERROR") -> None:
    if not condition:
        findings.append(Finding(severity, message))


def is_exact_string_list(value: Any, expected: frozenset[str]) -> bool:
    return (
        isinstance(value, list)
        and len(value) == len(expected)
        and all(isinstance(item, str) for item in value)
        and set(value) == expected
    )


def global_app_store_metadata_findings(path: Path) -> list[Finding]:
    findings: list[Finding] = []
    add(findings, path.exists(), f"Global App Store metadata draft is missing: {path}")
    if not path.exists():
        return findings

    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        return [Finding("ERROR", f"Invalid global App Store metadata: {error}")]

    if not isinstance(document, dict):
        return [Finding("ERROR", "Global App Store metadata root must be an object")]

    app_record = document.get("app_record", {})
    if not isinstance(app_record, dict):
        app_record = {}
    add(
        findings,
        app_record.get("planned_primary_locale") == "en-US",
        "Global planned primary locale must be en-US",
    )

    scope = document.get("version_1_1_localization_scope", {})
    if not isinstance(scope, dict):
        scope = {}
    add(
        findings,
        is_exact_string_list(scope.get("app_ui_locales"), VERSION_1_1_APP_LOCALES),
        "Version 1.1 app UI locales must be exactly ja and en",
    )
    add(
        findings,
        is_exact_string_list(scope.get("preserved_content_locales"), {"ja", "en", "ko"}),
        "Preserved learning content locales must remain ja, en, and ko",
    )
    add(
        findings,
        scope.get("unsupported_app_language_fallback") == "en",
        "Unsupported app language fallback must be en",
    )
    add(
        findings,
        is_exact_string_list(scope.get("app_store_metadata_locales"), GLOBAL_APP_STORE_LOCALES),
        "Version 1.1 App Store metadata locales must be exactly en-US, en-GB, en-AU, en-CA, ko, and ja",
    )
    add(
        findings,
        scope.get("android_m7") == ANDROID_M7_STATE,
        "Android M7 must remain on its resumed, separate Google Play release track",
    )

    availability = document.get("availability", {})
    if not isinstance(availability, dict):
        availability = {}
    add(
        findings,
        availability.get("selection") == ALL_COUNTRIES_SELECTION,
        "Version 1.1 availability must select All Countries or Regions",
    )
    add(
        findings,
        availability.get("include_future_storefronts") is True,
        "Version 1.1 availability must include future storefronts",
    )
    add(
        findings,
        availability.get("required_app_and_iap_match") is True,
        "App and Deck Maker IAP availability must match",
    )
    add(
        findings,
        availability.get("country_waves_enabled") is False,
        "Country availability waves must remain disabled",
    )

    localizations = document.get("localizations", {})
    if not isinstance(localizations, dict):
        localizations = {}
    add(
        findings,
        set(localizations) == GLOBAL_APP_STORE_LOCALES,
        "Global App Store localization keys must be exactly en-US, en-GB, en-AU, en-CA, ko, and ja",
    )
    for locale, expected_name in GLOBAL_APP_STORE_NAMES.items():
        values = localizations.get(locale, {})
        actual_name = values.get("name") if isinstance(values, dict) else None
        add(
            findings,
            actual_name == expected_name,
            f"{locale} global App Store name is {actual_name!r}, expected {expected_name!r}",
        )

    regional_gates = document.get("release_gates", {})
    if not isinstance(regional_gates, dict):
        regional_gates = {}
    for region in ("EU", "CN", "VN"):
        gates = regional_gates.get(region)
        add(
            findings,
            isinstance(gates, list) and bool(gates),
            f"Global availability requires a non-empty {region} compliance gate list",
        )

    return findings


def google_play_metadata_findings(path: Path, root: Path) -> list[Finding]:
    findings: list[Finding] = []
    add(findings, path.exists(), f"Google Play metadata draft is missing: {path}")
    if not path.exists():
        return findings

    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        return [Finding("ERROR", f"Invalid Google Play metadata: {error}")]
    if not isinstance(document, dict):
        return [Finding("ERROR", "Google Play metadata root must be an object")]

    add(findings, document.get("schema_version") == 1, "Google Play metadata schema must be 1")
    add(
        findings,
        document.get("status") == "draft_not_applied",
        "Google Play metadata must remain draft_not_applied until Console work is recorded",
    )
    add(
        findings,
        document.get("application_id") == "app.piyokey.piyokey",
        "Google Play application ID candidate differs",
    )
    add(
        findings,
        document.get("application_id_confirmed") is False,
        "Google Play application ID must remain unconfirmed until Play ownership is checked",
    )

    release = document.get("release", {})
    if not isinstance(release, dict):
        release = {}
    add(findings, release.get("version_name") == "1.1.0", "Google Play version name must be 1.1.0")
    add(findings, release.get("version_code") == 8, "Google Play version code must be 8")
    add(findings, release.get("target_sdk") == 36, "Google Play target SDK must be 36")
    add(findings, release.get("category") == "EDUCATION", "Google Play category must be Education")
    add(findings, release.get("contains_ads") is False, "Google Play listing must declare no ads")
    add(findings, release.get("default_language") == "en-US", "Google Play default language must be en-US")

    contact = document.get("contact", {})
    if not isinstance(contact, dict):
        contact = {}
    email = contact.get("email")
    add(
        findings,
        isinstance(email, str) and re.fullmatch(r"[^@\s]+@[^@\s]+\.[^@\s]+", email) is not None,
        "Google Play contact email is invalid",
    )
    for key in ("website", "privacy_policy"):
        value = contact.get(key)
        add(
            findings,
            isinstance(value, str) and value.startswith("https://"),
            f"Google Play {key} must be an HTTPS URL",
        )

    localizations = document.get("localizations", {})
    if not isinstance(localizations, dict):
        localizations = {}
    add(
        findings,
        set(localizations) == GOOGLE_PLAY_LOCALES,
        "Google Play localization keys must be exactly en-US, ja, and ko",
    )
    prohibited_promotional_copy = re.compile(
        r"(?:#\s*1|no\.\s*1|best\s+app|download\s+now|install\s+now|limited\s+time|\bfree\b|무료|無料)",
        re.IGNORECASE,
    )
    for locale in sorted(GOOGLE_PLAY_LOCALES):
        values = localizations.get(locale, {})
        if not isinstance(values, dict):
            values = {}
        for key, limit in (("title", 30), ("short_description", 80), ("full_description", 4_000)):
            value = values.get(key)
            add(
                findings,
                isinstance(value, str) and bool(value.strip()),
                f"Missing {locale} Google Play field: {key}",
            )
            if isinstance(value, str):
                add(findings, len(value) <= limit, f"{locale} Google Play {key} exceeds {limit} characters")
                add(
                    findings,
                    prohibited_promotional_copy.search(value) is None,
                    f"{locale} Google Play {key} contains prohibited promotional copy",
                )
        short_description = values.get("short_description")
        if isinstance(short_description, str):
            add(
                findings,
                "\n" not in short_description and "\r" not in short_description,
                f"{locale} Google Play short description must be one line",
            )

    release_notes = document.get("release_notes", {})
    if not isinstance(release_notes, dict):
        release_notes = {}
    add(
        findings,
        set(release_notes) == GOOGLE_PLAY_LOCALES,
        "Google Play release notes must cover exactly en-US, ja, and ko",
    )
    for locale, value in release_notes.items():
        add(
            findings,
            isinstance(value, str) and bool(value.strip()) and len(value) <= 500,
            f"{locale} Google Play release notes must contain at most 500 characters",
        )

    product = document.get("in_app_product", {})
    if not isinstance(product, dict):
        product = {}
    add(findings, product.get("product_id") == DECK_MAKER_PRODUCT_ID, "Google Play Deck Maker product ID differs")
    add(findings, product.get("product_type") == "ONE_TIME_PRODUCT", "Google Play Deck Maker must be a one-time product")
    add(findings, product.get("purchase_option_id") == "lifetime", "Google Play Deck Maker purchase option ID differs")
    product_localizations = product.get("localizations", {})
    if not isinstance(product_localizations, dict):
        product_localizations = {}
    add(
        findings,
        set(product_localizations) == GOOGLE_PLAY_LOCALES,
        "Google Play Deck Maker localizations must cover exactly en-US, ja, and ko",
    )
    for locale, expected in DECK_MAKER_LOCALIZATIONS.items():
        values = product_localizations.get(locale, {})
        actual = (values.get("name"), values.get("description")) if isinstance(values, dict) else (None, None)
        add(findings, actual == expected, f"Google Play Deck Maker {locale} metadata differs: {actual!r}")

    assets = document.get("assets", {})
    if not isinstance(assets, dict):
        assets = {}
    expected_assets = {
        "app_icon": ((512, 512), True, 1_048_576),
        "feature_graphic": ((1024, 500), False, None),
    }
    for key, (expected_size, expected_alpha, max_bytes) in expected_assets.items():
        contract = assets.get(key, {})
        if not isinstance(contract, dict):
            contract = {}
        relative_path = contract.get("path")
        add(findings, isinstance(relative_path, str) and bool(relative_path), f"Google Play {key} path is missing")
        if not isinstance(relative_path, str) or not relative_path:
            continue
        asset_path = root / relative_path
        add(findings, asset_path.exists(), f"Google Play asset is missing: {relative_path}")
        if not asset_path.exists():
            continue
        try:
            width, height, has_alpha = png_info(asset_path)
            add(
                findings,
                (width, height) == expected_size,
                f"Google Play {key} is {width}x{height}, expected {expected_size[0]}x{expected_size[1]}",
            )
            add(findings, has_alpha is expected_alpha, f"Google Play {key} alpha channel differs")
            add(
                findings,
                contract.get("width") == expected_size[0] and contract.get("height") == expected_size[1],
                f"Google Play {key} metadata dimensions differ",
            )
            add(
                findings,
                contract.get("requires_alpha_channel") is expected_alpha,
                f"Google Play {key} metadata alpha contract differs",
            )
            if max_bytes is not None:
                add(
                    findings,
                    asset_path.stat().st_size <= max_bytes,
                    f"Google Play {key} exceeds {max_bytes} bytes",
                )
        except (OSError, ValueError, struct.error) as error:
            findings.append(Finding("ERROR", f"Invalid Google Play {key}: {error}"))

    feature = assets.get("feature_graphic", {})
    if not isinstance(feature, dict):
        feature = {}
    source_path = feature.get("source_path")
    add(
        findings,
        isinstance(source_path, str) and (root / source_path).exists(),
        "Google Play feature graphic source is missing",
    )
    alt_text = feature.get("alt_text", {})
    if not isinstance(alt_text, dict):
        alt_text = {}
    add(
        findings,
        set(alt_text) == GOOGLE_PLAY_LOCALES,
        "Google Play feature graphic alt text must cover en-US, ja, and ko",
    )
    for locale, value in alt_text.items():
        add(
            findings,
            isinstance(value, str) and bool(value.strip()) and len(value) <= 140,
            f"{locale} Google Play feature graphic alt text must contain at most 140 characters",
        )

    screenshots = assets.get("phone_screenshots", {})
    if not isinstance(screenshots, dict):
        screenshots = {}
    add(
        findings,
        screenshots.get("status") == "pending_exact_signed_release_candidate",
        "Google Play screenshots must remain pending the exact signed release candidate",
    )
    add(findings, screenshots.get("count_per_locale") == 4, "Google Play needs four phone screenshots per locale")
    add(findings, screenshots.get("required_size") == [1080, 1920], "Google Play phone screenshot size must be 1080x1920")
    add(
        findings,
        is_exact_string_list(screenshots.get("locales"), GOOGLE_PLAY_LOCALES),
        "Google Play screenshot locales must be exactly en-US, ja, and ko",
    )

    external_gates = document.get("external_gates", {})
    if not isinstance(external_gates, dict):
        external_gates = {}
    add(findings, set(external_gates) == GOOGLE_PLAY_EXTERNAL_GATES, "Google Play external gate keys differ")
    add(
        findings,
        all(value is False for value in external_gates.values()),
        "Google Play external gates must remain open until verified outside the repository",
    )
    return findings


def google_play_console_declaration_findings(path: Path) -> list[Finding]:
    findings: list[Finding] = []
    add(findings, path.exists(), f"Google Play Console declaration draft is missing: {path}")
    if not path.exists():
        return findings
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        return [Finding("ERROR", f"Invalid Google Play Console declaration draft: {error}")]
    if not isinstance(document, dict):
        return [Finding("ERROR", "Google Play Console declaration root must be an object")]

    add(findings, document.get("schema_version") == 1, "Google Play Console declaration schema must be 1")
    add(findings, document.get("status") == GOOGLE_PLAY_CONSOLE_STATUS, "Google Play Console declarations must remain operator-review drafts")
    source = document.get("source_candidate", {})
    add(
        findings,
        isinstance(source, dict)
        and source.get("application_id") == "app.piyokey.piyokey"
        and source.get("version_name") == "1.1.0"
        and source.get("version_code") == 8,
        "Google Play Console declaration source candidate differs",
    )
    access = document.get("app_access", {})
    add(
        findings,
        isinstance(access, dict)
        and access.get("all_features_available_without_developer_account") is True
        and access.get("restricted_access_credentials_required") is False
        and access.get("optional_google_play_games_sign_in") is True,
        "Google Play app-access draft differs from the accountless product contract",
    )
    ads = document.get("ads", {})
    add(
        findings,
        isinstance(ads, dict)
        and ads.get("contains_ads") is False
        and ads.get("advertising_sdk_present") is False
        and ads.get("advertising_id_permission_expected") is False,
        "Google Play ads draft differs from the no-ad contract",
    )
    target = document.get("target_audience_draft", {})
    add(
        findings,
        isinstance(target, dict)
        and target.get("operator_confirmation_required") is True
        and target.get("recommended_age_groups") == ["13-15", "16-17", "18+"]
        and target.get("designed_for_children") is False,
        "Google Play target-audience draft differs",
    )
    rating = document.get("content_rating_draft", {})
    add(
        findings,
        isinstance(rating, dict)
        and rating.get("operator_confirmation_required") is True
        and rating.get("app_or_game") == "APP"
        and rating.get("category") == "EDUCATION"
        and rating.get("contains_in_app_purchases") is True,
        "Google Play content-rating draft differs",
    )
    safety = document.get("data_safety_evidence", {})
    add(
        findings,
        isinstance(safety, dict)
        and safety.get("operator_confirmation_required") is True
        and safety.get("tracking") is False
        and safety.get("analytics_sdk_present") is False
        and safety.get("advertising_sdk_present") is False
        and safety.get("final_collects_or_shares_answer")
        == "UNRESOLVED_UNTIL_PRODUCTION_HOST_AND_SDK_DISCLOSURES_ARE_REVIEWED",
        "Google Play Data safety must remain unresolved pending host and SDK review",
    )
    boundaries = safety.get("off_device_boundaries", []) if isinstance(safety, dict) else []
    services = {value.get("service") for value in boundaries if isinstance(value, dict)}
    add(
        findings,
        services == {
            "Static HTTPS catalog host",
            "Google Play Billing Library 9.1.0",
            "Google Play Games Services v2 22.0.0",
            "User-selected mail or browser app",
        },
        "Google Play off-device data boundaries differ",
    )
    prohibited = document.get("permissions_prohibited", [])
    add(
        findings,
        set(prohibited) == {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "com.google.android.gms.permission.AD_ID",
        },
        "Google Play prohibited permission contract differs",
    )
    references = document.get("official_references", [])
    add(
        findings,
        isinstance(references, list)
        and len(references) == 4
        and all(isinstance(value, str) and value.startswith("https://") for value in references),
        "Google Play Console declaration references must contain four official HTTPS sources",
    )
    return findings


def game_center_contract_findings(info: dict[str, Any], source: str) -> list[Finding]:
    findings: list[Finding] = []
    add(
        findings,
        "GKLeaderboard.loadLeaderboards(IDs: nil)" in source,
        "Game Center runtime availability must load the server-provided leaderboard list",
    )
    add(
        findings,
        "releaseState.contains(.released)" in source,
        "Game Center runtime availability must exclude prereleased leaderboards",
    )
    leaderboard_source = source
    if "enum GameCenterLeaderboard" in source and "enum PiyoCupWeek" in source:
        leaderboard_source = source.split("enum GameCenterLeaderboard", 1)[1].split("enum PiyoCupWeek", 1)[0]
    known_ids = set(re.findall(r'case\s+\w+\s*=\s*"(piyokey\.[^"]+)"', leaderboard_source))
    parsed: dict[str, set[str]] = {}
    for key, label in (
        (GAME_CENTER_AVAILABILITY_KEY, "availability"),
        (GAME_CENTER_INTENDED_KEY, "intended"),
    ):
        raw_ids = info.get(key)
        add(findings, isinstance(raw_ids, list), f"{key} must be an array")
        if not isinstance(raw_ids, list):
            continue
        add(findings, bool(raw_ids), f"Game Center {label} contract must not be empty")
        add(
            findings,
            all(isinstance(value, str) and value for value in raw_ids),
            f"Game Center {label} IDs must be non-empty strings",
        )
        string_ids = [value for value in raw_ids if isinstance(value, str)]
        add(
            findings,
            len(string_ids) == len(set(string_ids)),
            f"Game Center {label} IDs must be unique",
        )
        parsed[key] = set(string_ids)
        unknown_ids = sorted(parsed[key] - known_ids)
        add(findings, not unknown_ids, f"Game Center {label} contains unknown IDs: {unknown_ids}")

    if GAME_CENTER_AVAILABILITY_KEY in parsed and GAME_CENTER_INTENDED_KEY in parsed:
        add(
            findings,
            parsed[GAME_CENTER_AVAILABILITY_KEY].issubset(parsed[GAME_CENTER_INTENDED_KEY]),
            "Game Center baseline availability IDs must be a subset of intended IDs",
        )
    return findings


def repository_checks(root: Path) -> list[Finding]:
    findings: list[Finding] = []
    app = root / "ios/Hanco/Hanco"
    project_path = root / "ios/Hanco/Hanco.xcodeproj/project.pbxproj"
    manifest_path = app / "Resources/PrivacyInfo.xcprivacy"
    info_path = app / "Resources/Info.plist"
    icon_path = app / "Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png"
    icon_source_path = root / "shared/brand/piyokey_app_icon_source.png"
    default_typing_sound_path = app / "Resources/Sounds/ui_basic_mouse_click_640020.caf"
    sound_provenance_path = app / "Resources/Sounds/README.md"
    app_settings_path = app / "Core/Settings/AppSettings.swift"
    game_center_service_path = app / "Core/GameCenter/GameCenterService.swift"
    metadata_path = root / "release/app_store_metadata.json"
    global_metadata_path = root / "release/global_app_store_metadata.json"
    google_play_metadata_path = root / "release/google_play_metadata.json"
    google_play_console_path = root / "release/google_play_console_declarations.json"
    purchase_source_path = app / "Core/Purchases/DeckMakerPurchaseStore.swift"
    storekit_config_path = app / "Resources/DeckMaker.storekit"
    scheme_path = root / "ios/Hanco/Hanco.xcodeproj/xcshareddata/xcschemes/Hanco.xcscheme"
    export_options_path = root / "release/ExportOptions.plist"
    upload_options_path = root / "release/ExportOptionsUpload.plist"
    android_manifest_path = root / "android/app/src/main/AndroidManifest.xml"
    android_build_path = root / "android/app/build.gradle.kts"
    android_file_paths = root / "android/app/src/main/res/xml/file_paths.xml"
    analytics_contract_path = root / "shared/analytics/events.json"
    analytics_doc_path = root / "docs/ANALYTICS.md"
    analytics_release_path = root / "release/analytics_release_state.json"
    analytics_privacy_draft_path = root / "release/PRIVACY_POLICY_ANALYTICS_DRAFT.md"
    play_data_safety_path = root / "release/PLAY_DATA_SAFETY_SETUP.md"
    web_analytics_path = root / "web/analytics/src/index.ts"
    ios_telemetry_path = app / "Core/Analytics/TelemetryService.swift"
    android_telemetry_path = root / "android/app/src/main/java/app/piyokey/piyokey/TelemetryRuntime.kt"

    for path in (
        project_path,
        manifest_path,
        info_path,
        icon_path,
        icon_source_path,
        default_typing_sound_path,
        sound_provenance_path,
        app_settings_path,
        game_center_service_path,
        metadata_path,
        global_metadata_path,
        google_play_metadata_path,
        google_play_console_path,
        purchase_source_path,
        storekit_config_path,
        scheme_path,
        export_options_path,
        upload_options_path,
        android_manifest_path,
        android_build_path,
        android_file_paths,
        analytics_contract_path,
        analytics_privacy_draft_path,
        play_data_safety_path,
        analytics_doc_path,
        analytics_release_path,
        web_analytics_path,
        ios_telemetry_path,
        android_telemetry_path,
    ):
        add(findings, path.exists(), f"Required file is missing: {path.relative_to(root)}")
    if findings:
        return findings

    findings.extend(pronunciation_audio_findings(root))

    try:
        manifest = load_plist(manifest_path)
        add(findings, manifest.get("NSPrivacyTracking") is False, "Privacy manifest must declare tracking=false")
        add(findings, manifest.get("NSPrivacyTrackingDomains") == [], "Tracking domains must be empty")
        collected_entries = manifest.get("NSPrivacyCollectedDataTypes", [])
        collected = {
            entry.get("NSPrivacyCollectedDataType"): set(entry.get("NSPrivacyCollectedDataTypePurposes", []))
            for entry in collected_entries
            if isinstance(entry, dict)
            and entry.get("NSPrivacyCollectedDataTypeLinked") is False
            and entry.get("NSPrivacyCollectedDataTypeTracking") is False
        }
        add(findings, collected == REQUIRED_COLLECTED_DATA, f"Collected data declarations differ: {collected!r}")
        entries = manifest.get("NSPrivacyAccessedAPITypes", [])
        reasons = {
            entry.get("NSPrivacyAccessedAPIType"): entry.get("NSPrivacyAccessedAPITypeReasons")
            for entry in entries
            if isinstance(entry, dict)
        }
        add(findings, reasons == REQUIRED_PRIVACY_REASONS, f"Required reason APIs differ: {reasons!r}")
    except (OSError, ValueError, plistlib.InvalidFileException) as error:
        findings.append(Finding("ERROR", f"Invalid privacy manifest: {error}"))

    project = project_path.read_text(encoding="utf-8")
    add(
        findings,
        "PrivacyInfo.xcprivacy in Resources" in project,
        "PrivacyInfo.xcprivacy is not included in the app resources build phase",
    )
    add(
        findings,
        "ui_basic_mouse_click_640020.caf in Resources" in project,
        "Default typing sound is not included in the app resources build phase",
    )
    add(
        findings,
        "com.apple.InAppPurchase" in project,
        "The app target must enable the In-App Purchase capability",
    )
    add(
        findings,
        "DeckMaker.storekit" in project,
        "DeckMaker.storekit must be visible in the Xcode project",
    )
    add(
        findings,
        "DeckMaker.storekit in Resources" not in project,
        "The local StoreKit configuration must not be embedded in the app bundle",
    )
    for required in (
        "PostHog in Frameworks",
        "FirebaseCrashlytics in Frameworks",
        "Upload Crashlytics Symbols",
        "AnalyticsContract.generated.swift in Sources",
        "TelemetryService.swift in Sources",
    ):
        add(findings, required in project, f"iOS telemetry project integration is missing: {required}")
    add(
        findings,
        hashlib.sha256(default_typing_sound_path.read_bytes()).hexdigest()
        == DEFAULT_TYPING_SOUND_SHA256,
        "Default typing sound checksum differs from the reviewed CC0 asset",
    )
    provenance = sound_provenance_path.read_text(encoding="utf-8")
    add(
        findings,
        "freesound.org/people/Philip_Berger/sounds/640020/" in provenance,
        "Typing sound source URL is missing",
    )
    add(
        findings,
        "creativecommons.org/publicdomain/zero/1.0/" in provenance,
        "Typing sound CC0 license URL is missing",
    )

    app_settings = app_settings_path.read_text(encoding="utf-8")
    for url in (
        "https://hancoweb.vercel.app/privacy",
        "https://hancoweb.vercel.app/support",
    ):
        add(findings, url in app_settings, f"Settings must expose release URL: {url}")

    try:
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        add(findings, metadata.get("primary_locale") == "ja", "Primary App Store locale must be Japanese")
        add(findings, metadata.get("primary_category") == "EDUCATION", "Primary category must be Education")
        localizations = metadata.get("localizations", {})
        add(findings, set(localizations) == {"ja", "en-US", "ko"}, "Store localizations must be ja, en-US, and ko")
        for locale, values in localizations.items():
            if not isinstance(values, dict):
                findings.append(Finding("ERROR", f"Invalid store localization: {locale}"))
                continue
            for key, limit in (
                ("name", 30),
                ("subtitle", 30),
                ("promotional_text", 170),
                ("description", 4_000),
                ("whats_new", 4_000),
            ):
                value = values.get(key, "")
                add(findings, isinstance(value, str) and bool(value), f"Missing {locale} store field: {key}")
                if isinstance(value, str):
                    add(findings, len(value) <= limit, f"{locale} {key} exceeds {limit} characters")
            keywords = values.get("keywords", "")
            add(findings, isinstance(keywords, str) and bool(keywords), f"Missing {locale} store field: keywords")
            if isinstance(keywords, str):
                add(findings, len(keywords.encode("utf-8")) <= 100, f"{locale} keywords exceed 100 UTF-8 bytes")
        privacy = metadata.get("app_privacy", {})
        add(findings, privacy.get("tracking") is False, "Store privacy must declare no tracking")
        add(findings, privacy.get("data_collected") is True, "Store privacy must declare optional collected data")
        data_types = privacy.get("data_types", {})
        add(
            findings,
            set(data_types) == {
                "product_interaction",
                "other_usage_data",
                "gameplay_content",
                "purchase_history",
                "crash_data",
                "other_diagnostic_data",
                "device_id",
            },
            "Store privacy collected data types differ",
        )
        for name, value in data_types.items():
            add(findings, value.get("linked_to_user") is False, f"Store privacy {name} must not be linked")
            add(findings, value.get("used_for_tracking") is False, f"Store privacy {name} must not track")

        iap = metadata.get("in_app_purchase", {})
        add(findings, iap.get("product_id") == DECK_MAKER_PRODUCT_ID, "Deck Maker product ID differs")
        add(findings, iap.get("type") == "NON_CONSUMABLE", "Deck Maker must be non-consumable")
        iap_localizations = iap.get("localizations", {})
        add(
            findings,
            set(iap_localizations) == set(DECK_MAKER_LOCALIZATIONS),
            "Deck Maker localizations must be ja, en-US, and ko",
        )
        for locale, expected in DECK_MAKER_LOCALIZATIONS.items():
            values = iap_localizations.get(locale, {})
            actual = (values.get("display_name"), values.get("description"))
            add(findings, actual == expected, f"Deck Maker {locale} metadata differs: {actual!r}")
            if all(isinstance(value, str) for value in actual):
                add(findings, 2 <= len(actual[0]) <= 30, f"Deck Maker {locale} display name length is invalid")
                add(findings, len(actual[1]) <= 45, f"Deck Maker {locale} description exceeds 45 characters")

        stale_claims = ("no in-app purchases", "アプリ内課金なし", "앱 내 결제 없음")
        for locale, values in localizations.items():
            description = values.get("description", "") if isinstance(values, dict) else ""
            add(
                findings,
                not any(claim in description.lower() for claim in stale_claims),
                f"{locale} description still claims that the app has no in-app purchases",
            )
    except (OSError, json.JSONDecodeError) as error:
        findings.append(Finding("ERROR", f"Invalid App Store metadata: {error}"))

    findings.extend(global_app_store_metadata_findings(global_metadata_path))
    findings.extend(google_play_metadata_findings(google_play_metadata_path, root))
    findings.extend(google_play_console_declaration_findings(google_play_console_path))

    purchase_source = purchase_source_path.read_text(encoding="utf-8")
    for contract in (
        DECK_MAKER_PRODUCT_ID,
        "Transaction.currentEntitlements",
        "Transaction.updates",
        "AppStore.sync()",
        "case .verified",
    ):
        add(findings, contract in purchase_source, f"Deck Maker StoreKit contract is missing: {contract}")

    try:
        config = json.loads(storekit_config_path.read_text(encoding="utf-8"))
        products = config.get("products", [])
        add(findings, len(products) == 1, "DeckMaker.storekit must contain exactly one product")
        product = products[0] if len(products) == 1 and isinstance(products[0], dict) else {}
        add(findings, product.get("productID") == DECK_MAKER_PRODUCT_ID, "StoreKit test product ID differs")
        add(findings, product.get("type") == "NonConsumable", "StoreKit test product must be non-consumable")
        configured = {
            value.get("locale"): (value.get("displayName"), value.get("description"))
            for value in product.get("localizations", [])
            if isinstance(value, dict)
        }
        expected = {
            STOREKIT_LOCALE_MAP[locale]: values
            for locale, values in DECK_MAKER_LOCALIZATIONS.items()
        }
        add(findings, configured == expected, f"StoreKit test localizations differ: {configured!r}")
    except (OSError, json.JSONDecodeError) as error:
        findings.append(Finding("ERROR", f"Invalid DeckMaker.storekit: {error}"))

    try:
        scheme_root = ET.parse(scheme_path).getroot()
        launch_action = scheme_root.find("./LaunchAction")
        add(
            findings,
            launch_action is not None and launch_action.get("buildConfiguration") == "Debug",
            "The shared scheme LaunchAction using local StoreKit must remain Debug-only",
        )
        launch_reference = scheme_root.find("./LaunchAction/StoreKitConfigurationFileReference")
        add(findings, launch_reference is not None, "Debug LaunchAction must select DeckMaker.storekit")
        if launch_reference is not None:
            add(
                findings,
                launch_reference.get("identifier") == "../../Hanco/Resources/DeckMaker.storekit",
                "Debug LaunchAction StoreKit configuration path differs",
            )
        add(
            findings,
            scheme_root.find("./ArchiveAction/StoreKitConfigurationFileReference") is None,
            "ArchiveAction must not use a local StoreKit configuration",
        )
        add(
            findings,
            scheme_root.find("./ProfileAction/StoreKitConfigurationFileReference") is None,
            "Release ProfileAction must not use a local StoreKit configuration",
        )
    except (OSError, ET.ParseError) as error:
        findings.append(Finding("ERROR", f"Invalid shared Hanco scheme: {error}"))

    try:
        export_options = load_plist(export_options_path)
        add(findings, export_options.get("method") == "app-store-connect", "Export method must be app-store-connect")
        add(findings, export_options.get("destination") == "export", "Export destination must be export")
        add(findings, export_options.get("signingStyle") == "automatic", "Export signing must be automatic")
        add(findings, export_options.get("teamID") == "X44BQNTAH9", "Export team must be X44BQNTAH9")
        add(
            findings,
            export_options.get("manageAppVersionAndBuildNumber") is False,
            "Export must preserve the reviewed version and build number",
        )
    except (OSError, ValueError, plistlib.InvalidFileException) as error:
        findings.append(Finding("ERROR", f"Invalid export options: {error}"))

    try:
        upload_options = load_plist(upload_options_path)
        add(findings, upload_options.get("method") == "app-store-connect", "Upload method must be app-store-connect")
        add(findings, upload_options.get("destination") == "upload", "Upload destination must be upload")
        add(findings, upload_options.get("signingStyle") == "automatic", "Upload signing must be automatic")
        add(findings, upload_options.get("teamID") == "X44BQNTAH9", "Upload team must be X44BQNTAH9")
        add(
            findings,
            upload_options.get("manageAppVersionAndBuildNumber") is False,
            "Upload must preserve the reviewed version and build number",
        )
    except (OSError, ValueError, plistlib.InvalidFileException) as error:
        findings.append(Finding("ERROR", f"Invalid upload options: {error}"))

    try:
        info = load_plist(info_path)
        add(findings, info.get("CFBundleDisplayName") == "typee", "Base display name must be typee")
        add(findings, info.get("CFBundleName") == "typee", "Base bundle name must be typee")
        add(
            findings,
            info.get("LSApplicationCategoryType") == "public.app-category.education",
            "App category must be Education",
        )
        add(findings, info.get("ITSAppUsesNonExemptEncryption") is False, "Export compliance plist declaration is missing")
        orientations = info.get("UISupportedInterfaceOrientations", [])
        add(findings, orientations == ["UIInterfaceOrientationPortrait"], "Only portrait orientation should be declared")
        findings.extend(
            game_center_contract_findings(
                info,
                game_center_service_path.read_text(encoding="utf-8"),
            )
        )
    except (OSError, ValueError, plistlib.InvalidFileException) as error:
        findings.append(Finding("ERROR", f"Invalid Info.plist: {error}"))

    for language, expected_name in EXPECTED_DISPLAY_NAMES.items():
        strings_path = app / f"Resources/{language}.lproj/InfoPlist.strings"
        add(findings, strings_path.exists(), f"Missing {language} InfoPlist.strings")
        if strings_path.exists():
            info_strings = parse_strings(strings_path)
            actual = info_strings.get("CFBundleDisplayName")
            add(findings, actual == expected_name, f"{language} display name is {actual!r}, expected {expected_name!r}")
            bundle_name = info_strings.get("CFBundleName")
            add(findings, bundle_name == expected_name, f"{language} bundle name is {bundle_name!r}, expected {expected_name!r}")

    localized: dict[str, set[str]] = {}
    for language in EXPECTED_DISPLAY_NAMES:
        strings_path = app / f"Resources/{language}.lproj/Localizable.strings"
        add(findings, strings_path.exists(), f"Missing {language} Localizable.strings")
        if strings_path.exists():
            localized[language] = set(parse_strings(strings_path))
    if len(localized) == len(EXPECTED_DISPLAY_NAMES):
        reference = localized["ja"]
        for language, keys in localized.items():
            add(findings, keys == reference, f"{language} localization keys differ from Japanese")

    try:
        width, height, has_alpha = png_info(icon_path)
        add(findings, (width, height) == (1024, 1024), f"AppIcon is {width}x{height}, expected 1024x1024")
        add(findings, not has_alpha, "AppIcon must not contain an alpha channel")
        add(
            findings,
            icon_path.read_bytes() == icon_source_path.read_bytes(),
            "AppIcon must match the shared typee / ピヨキー brand source",
        )
    except (OSError, ValueError, struct.error) as error:
        findings.append(Finding("ERROR", f"Invalid AppIcon: {error}"))

    try:
        android_manifest = android_manifest_path.read_text(encoding="utf-8")
        android_build = android_build_path.read_text(encoding="utf-8")
        android_paths = android_file_paths.read_text(encoding="utf-8")
        add(findings, 'android:icon="@mipmap/ic_launcher"' in android_manifest, "Android launcher icon is missing")
        add(findings, 'android:roundIcon="@mipmap/ic_launcher_round"' in android_manifest, "Android round launcher icon is missing")
        add(findings, '${applicationId}.files' in android_manifest, "Android FileProvider must use the application ID authority")
        add(findings, 'android:exported="false"' in android_manifest, "Android FileProvider must not be exported")
        add(
            findings,
            'android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28"' in android_manifest,
            "Android legacy image-save permission must be limited to API 28",
        )
        for prohibited_permission in (
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "com.google.android.gms.permission.AD_ID",
        ):
            add(
                findings,
                prohibited_permission not in android_manifest,
                f"Android manifest must not request {prohibited_permission}",
            )
        add(findings, 'android:allowBackup="false"' in android_manifest, "Android local-only data must not be backed up")
        add(findings, 'path="shared_results/"' in android_paths, "Android share provider must expose only result cache files")
        add(
            findings,
            "../shared/brand/piyokey_app_icon_source.png" in android_build
            and 'rename { "piyokey_logo.png" }' in android_build,
            "Android launcher/share logo must derive from the shared brand source",
        )
        add(findings, 'android.permission.INTERNET' in android_manifest, "Android telemetry requires INTERNET permission")
        add(
            findings,
            'firebase_crashlytics_collection_enabled' in android_manifest and 'android:value="false"' in android_manifest,
            "Android Crashlytics collection must default to false",
        )
        for required in (
            "libs.posthog.android",
            "libs.firebase.crashlytics",
            'project(":core:analytics")',
            'PIYOKEY_ANALYTICS_PRIVACY_CONFIRMED',
        ):
            add(findings, required in android_build, f"Android telemetry build integration is missing: {required}")
    except OSError as error:
        findings.append(Finding("ERROR", f"Invalid Android release resources: {error}"))

    try:
        contract = json.loads(analytics_contract_path.read_text(encoding="utf-8"))
        add(findings, contract.get("schema_version") == 1, "Analytics contract schema version differs")
        add(findings, len(contract.get("events", {})) >= 10, "Analytics contract has too few semantic events")
        forbidden = set(contract.get("forbidden_properties", []))
        allowed = set(contract.get("properties", {}))
        add(findings, not (forbidden & allowed), "Forbidden analytics properties are allowlisted")
        add(
            findings,
            {"text", "input", "answer", "user_deck_id", "path", "receipt"} <= forbidden,
            "Analytics sensitive-property denylist differs",
        )
        release_state = json.loads(analytics_release_path.read_text(encoding="utf-8"))
        add(findings, release_state.get("schema_version") == 1, "Analytics release state schema differs")
        add(findings, release_state.get("provider_region") == "posthog_cloud_eu", "Analytics region must be PostHog EU")
        add(findings, release_state.get("free_tier_only") is True, "Analytics must remain free-tier only")
        add(findings, bool(release_state.get("gates")), "Analytics release gates are missing")
        required_gates = {
            "posthog_geoip_disabled",
            "consent_notice_ui_verified",
            "privacy_policy_published",
            "privacy_retention_and_deletion_verified",
            "live_privacy_copy_matches_build",
            "app_store_privacy_updated",
            "play_data_safety_updated",
        }
        add(
            findings,
            required_gates <= set(release_state.get("gates", {})),
            "Analytics privacy release gates differ",
        )
    except (OSError, json.JSONDecodeError) as error:
        findings.append(Finding("ERROR", f"Invalid analytics contract or release state: {error}"))

    ios_telemetry = ios_telemetry_path.read_text(encoding="utf-8")
    android_telemetry = android_telemetry_path.read_text(encoding="utf-8")
    web_telemetry = web_analytics_path.read_text(encoding="utf-8")
    for source, label in (
        (ios_telemetry, "iOS"),
        (android_telemetry, "Android"),
        (web_telemetry, "Web"),
    ):
        add(findings, "sessionReplay" in source or "disable_session_recording" in source, f"{label} replay disable is missing")
        add(findings, "personProfiles" in source or "person_profiles" in source, f"{label} person-profile disable is missing")
        add(findings, "$geoip_disable" in source, f"{label} PostHog GeoIP disable is missing")

    return findings


def strict_checks(root: Path) -> list[Finding]:
    findings: list[Finding] = []
    project_path = root / "ios/Hanco/Hanco.xcodeproj/project.pbxproj"
    submission_path = root / "release/app_store_submission.json"
    screenshot_dir = root / "release/screenshots/ja-marketing"
    metadata_path = root / "release/app_store_metadata.json"
    analytics_release_path = root / "release/analytics_release_state.json"

    add(findings, submission_path.exists(), "release/app_store_submission.json is missing")
    add(findings, analytics_release_path.exists(), "release/analytics_release_state.json is missing")
    if not submission_path.exists():
        return findings
    try:
        submission_document = json.loads(submission_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        return [Finding("ERROR", f"Invalid submission metadata: {error}")]
    submission = submission_document.get("next_submission", submission_document)

    if analytics_release_path.exists():
        try:
            analytics_state = json.loads(analytics_release_path.read_text(encoding="utf-8"))
            for key, value in analytics_state.get("gates", {}).items():
                add(findings, value is True, f"Analytics release gate is open: {key}")
        except (OSError, json.JSONDecodeError) as error:
            findings.append(Finding("ERROR", f"Invalid analytics release state: {error}"))
    add(
        findings,
        submission.get("marketing_version") == "1.1",
        "Next submission record must target version 1.1",
    )

    project = project_path.read_text(encoding="utf-8")
    bundle_ids = {
        value.strip('"')
        for value in re.findall(r"PRODUCT_BUNDLE_IDENTIFIER = ([^;]+);", project)
        if not value.strip('"').endswith((".tests", ".uitests"))
    }
    versions = {value.strip('"') for value in re.findall(r"MARKETING_VERSION = ([^;]+);", project)}
    builds = {value.strip('"') for value in re.findall(r"CURRENT_PROJECT_VERSION = ([^;]+);", project)}
    teams = {value.strip('"') for value in re.findall(r"DEVELOPMENT_TEAM = ([^;]+);", project)}
    add(findings, len(bundle_ids) == 1, f"App target must have one Bundle ID: {sorted(bundle_ids)}")
    add(findings, teams == {"X44BQNTAH9"}, f"All targets must use release team X44BQNTAH9: {sorted(teams)}")
    add(findings, not any("prototype" in value.lower() for value in bundle_ids), "Prototype Bundle ID must be replaced")
    add(findings, submission.get("bundle_id") in bundle_ids, "Submission Bundle ID must match the Xcode app target")
    add(findings, submission.get("developer_team_id") == "X44BQNTAH9", "Submission Developer Team must match Xcode")
    add(
        findings,
        str(submission.get("app_store_connect_app_id", "")).isdigit(),
        "App Store Connect app ID is required",
    )
    add(findings, submission.get("marketing_version") in versions, "Submission marketing version must match Xcode")
    add(findings, str(submission.get("build_number", "")) in builds, "Submission build number must match Xcode")

    for key in ("privacy_policy_url", "support_url"):
        value = submission.get(key, "")
        add(findings, isinstance(value, str) and value.startswith("https://"), f"{key} must be an HTTPS URL")
    try:
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        for key in ("privacy_policy_url", "support_url", "marketing_url"):
            add(findings, submission.get(key) == metadata.get(key), f"Submission {key} must match store metadata")
    except (OSError, json.JSONDecodeError):
        pass
    add(findings, bool(submission.get("copyright")), "Copyright is required")
    add(findings, submission.get("content_rights_confirmed") is True, "Content rights are not confirmed")
    add(
        findings,
        submission.get("release_mode") in {"manual_after_approval", "automatic_after_approval"},
        "Next submission release mode must be resolved independently of availability",
    )

    availability = submission.get("availability", {})
    if not isinstance(availability, dict):
        availability = {}
    add(
        findings,
        availability.get("selection") == ALL_COUNTRIES_SELECTION,
        "Submission availability must select All Countries or Regions",
    )
    add(
        findings,
        availability.get("include_future_storefronts") is True,
        "Submission availability must include future storefronts",
    )
    add(
        findings,
        availability.get("app_and_iap_match") is True,
        "Submission app and IAP availability must match",
    )
    add(
        findings,
        availability.get("country_waves_enabled") is False,
        "Submission country waves must remain disabled",
    )
    add(
        findings,
        availability.get("regional_storefront_status_review_completed") is True,
        "Regional storefront statuses must be reviewed before submission",
    )
    add(
        findings,
        isinstance(availability.get("regional_compliance_exceptions"), list),
        "Regional compliance exceptions must be recorded as a list",
    )

    review = submission.get("app_review", {})
    for key in ("contact_name", "contact_email", "contact_phone"):
        add(findings, bool(review.get(key)), f"App Review {key} is required")
    add(findings, review.get("notes_prepared") is True, "App Review notes are not prepared")

    iap = submission.get("in_app_purchase", {})
    add(findings, iap.get("product_id") == DECK_MAKER_PRODUCT_ID, "Submission Deck Maker product ID differs")
    add(findings, iap.get("type") == "NON_CONSUMABLE", "Submission Deck Maker type differs")
    add(
        findings,
        set(iap.get("localizations", [])) == set(DECK_MAKER_LOCALIZATIONS),
        "Submission Deck Maker localizations must be ja, en-US, and ko",
    )
    for key in (
        "paid_apps_agreement_active",
        "tax_and_banking_active",
        "small_business_program_status_checked",
        "price_and_availability_configured",
        "tax_category_confirmed",
        "family_sharing_policy_confirmed",
        "review_screenshot_uploaded",
        "review_notes_saved",
        "ready_to_submit",
        "added_for_review",
        "attached_to_version_1_1_submission",
    ):
        add(findings, iap.get(key) is True, f"In-app purchase gate is open: {key}")

    gates = submission.get("qa_gates", {})
    for key in (
        "release_archive_validated",
        "full_unit_and_ui_regression",
        "testflight_clean_install_smoke",
        "korean_os_ime_device_check",
        "tts_device_check",
        "silent_mode_sound_device_check",
        "external_audio_mix_device_check",
        "iphone_12_performance_gate",
        "game_center_live_device_check",
        "deck_maker_local_storekit_check",
        "deck_maker_sandbox_purchase_check",
        "deck_maker_sandbox_cancel_check",
        "deck_maker_sandbox_pending_check",
        "deck_maker_sandbox_restore_check",
        "deck_maker_sandbox_refund_revocation_check",
        "free_piyodeck_boundary_check",
    ):
        add(findings, gates.get(key) is True, f"QA gate is open: {key}")

    screenshots = sorted(path for path in screenshot_dir.glob("*.png")) if screenshot_dir.exists() else []
    add(findings, 1 <= len(screenshots) <= 10, "Japanese App Store screenshots must contain 1 to 10 PNG files")
    for screenshot in screenshots:
        try:
            width, height, has_alpha = png_info(screenshot)
            add(findings, (width, height) in IPHONE_SCREENSHOT_SIZES, f"Unsupported screenshot size: {screenshot.name} {width}x{height}")
            add(findings, not has_alpha, f"Screenshot has alpha: {screenshot.name}")
        except (OSError, ValueError, struct.error) as error:
            findings.append(Finding("ERROR", f"Invalid screenshot {screenshot.name}: {error}"))

    screenshot_manifest_path = screenshot_dir / "release-manifest.json"
    add(findings, screenshot_manifest_path.exists(), "Japanese screenshot manifest is missing")
    if screenshot_manifest_path.exists():
        try:
            screenshot_manifest = json.loads(screenshot_manifest_path.read_text(encoding="utf-8"))
            add(findings, screenshot_manifest.get("locale") == "ja", "Screenshot manifest locale must be ja")
            add(
                findings,
                screenshot_manifest.get("captured_from_bundle_id") == submission.get("bundle_id"),
                "Screenshot manifest Bundle ID must match the submission",
            )
            entries = screenshot_manifest.get("screenshots", [])
            manifest_files = {
                entry.get("file"): entry
                for entry in entries
                if isinstance(entry, dict) and isinstance(entry.get("file"), str)
            }
            add(
                findings,
                set(manifest_files) == {path.name for path in screenshots},
                "Screenshot manifest must list every PNG exactly once",
            )
            for screenshot in screenshots:
                entry = manifest_files.get(screenshot.name, {})
                actual_hash = hashlib.sha256(screenshot.read_bytes()).hexdigest()
                add(findings, entry.get("sha256") == actual_hash, f"Screenshot checksum differs: {screenshot.name}")
                add(findings, bool(entry.get("source_test")), f"Screenshot source test is missing: {screenshot.name}")
        except (OSError, json.JSONDecodeError) as error:
            findings.append(Finding("ERROR", f"Invalid screenshot manifest: {error}"))

    return findings


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()

    root = args.root.resolve()
    findings = repository_checks(root)
    if args.strict:
        findings.extend(strict_checks(root))

    if findings:
        for finding in findings:
            print(f"[{finding.severity}] {finding.message}")
        print(f"FAIL: {len(findings)} release finding(s)")
        return 1

    mode = "strict submission" if args.strict else "repository"
    print(f"PASS: typee / ピヨキー {mode} preflight")
    return 0


if __name__ == "__main__":
    sys.exit(main())
