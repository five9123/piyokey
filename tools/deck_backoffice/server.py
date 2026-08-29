#!/usr/bin/env python3
"""Local PIYOKEY deck authoring server.

The app runtime keeps consuming a read-only static catalog. This server only
binds to loopback and edits repository fixtures plus durable generator
overrides when an operator explicitly presses Save in the browser UI.
"""

from __future__ import annotations

import argparse
import importlib.metadata
import importlib.util
import json
import os
import re
import threading
import urllib.error
import urllib.request
import webbrowser
from collections import Counter
from datetime import datetime, timezone
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path, PurePosixPath
from typing import Any
from urllib.parse import parse_qs, urlparse


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
STATIC_ROOT = Path(__file__).resolve().parent / "static"
IDENTIFIER_PATTERN = re.compile(r"^[a-z0-9][a-z0-9_-]{2,63}$")
ALLOWED_FOLDERS = {"", "flow", "acid_rain", "word_match", "choseong", "dictation"}
ALLOWED_LITERAL_PUNCTUATION = set(" .,!?…'\"()-·~♡♥。！？")
DEFAULT_OPENAI_MODEL = "gpt-5.6-luna"
DEFAULT_OPENAI_ENDPOINT = "https://api.openai.com/v1/responses"
REQUIRED_GTTS_VERSION = "2.5.4"
GTTS_LANGUAGE = "ko"
GTTS_TLD = "com"
GTTS_SLOW = False
GTTS_ATTEMPTS = 3
COMPATIBILITY_JAMO = set(
    "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
    "ㄳㄵㄶㄺㄻㄼㄽㄾㄿㅀㅄ"
)
DECK_FIELDS = {
    "deck_id",
    "version",
    "name",
    "author",
    "official",
    "type",
    "level",
    "tags",
    "created_at",
    "updated_at",
    "items",
}
ITEM_FIELDS = {"id", "ko", "reading_ja", "meaning_ja", "audio"}
OPTIONAL_DECK_FIELDS = {"localizations"}
OPTIONAL_ITEM_FIELDS = {"localizations"}
SUPPORTED_LOCALIZATION_CODES = {"en", "ko", "es", "de", "fr"}


_GTTS_GENERATOR: Any | None = None
_GTTS_GENERATOR_LOCK = threading.Lock()


def _load_gtts_generator() -> Any:
    """Load the repository generator by exact path without importing gTTS itself."""
    global _GTTS_GENERATOR
    if _GTTS_GENERATOR is not None:
        return _GTTS_GENERATOR
    with _GTTS_GENERATOR_LOCK:
        if _GTTS_GENERATOR is not None:
            return _GTTS_GENERATOR
        module_path = REPOSITORY_ROOT / "tools" / "gen_gtts_audio.py"
        spec = importlib.util.spec_from_file_location("piyokey_gen_gtts_audio", module_path)
        if spec is None or spec.loader is None:
            raise RuntimeError(f"gTTS generator를 불러올 수 없습니다: {module_path}")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        required_functions = ("audio_asset_path", "generate_gtts", "validate_mp3")
        if any(not callable(getattr(module, name, None)) for name in required_functions):
            raise RuntimeError("gTTS generator의 필수 함수가 없습니다.")
        _GTTS_GENERATOR = module
        return module


class RequestError(Exception):
    def __init__(self, status: int, message: str, issues: list[dict[str, str]] | None = None):
        super().__init__(message)
        self.status = status
        self.message = message
        self.issues = issues or []


def issue(severity: str, code: str, path: str, message: str) -> dict[str, str]:
    return {"severity": severity, "code": code, "path": path, "message": message}


def _is_int(value: Any) -> bool:
    return isinstance(value, int) and not isinstance(value, bool)


def _nonempty_string(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())


def _parse_iso_date(value: Any) -> datetime | None:
    if not isinstance(value, str):
        return None
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None


def _is_supported_korean_target(value: str) -> tuple[bool, str | None]:
    has_hangul = False
    for character in value:
        scalar = ord(character)
        if 0xAC00 <= scalar <= 0xD7A3 or character in COMPATIBILITY_JAMO:
            has_hangul = True
            continue
        if character in ALLOWED_LITERAL_PUNCTUATION or character.isspace() or character.isnumeric():
            continue
        return False, character
    return has_hangul, None


def _validate_localized_tags(
    value: Any,
    *,
    path: str,
    base_tag_count: int | None,
    issues: list[dict[str, str]],
) -> None:
    if not isinstance(value, list):
        issues.append(issue("error", "type", path, "현지화 태그는 배열이어야 합니다."))
        return
    if not 1 <= len(value) <= 8:
        issues.append(issue("error", "tag_count", path, "현지화 태그는 1~8개여야 합니다."))
    if base_tag_count is not None and len(value) != base_tag_count:
        issues.append(
            issue(
                "error",
                "localized_tag_count",
                path,
                f"원본 태그 수 {base_tag_count}개와 일치해야 합니다.",
            )
        )
    if len(set(tag for tag in value if isinstance(tag, str))) != len(value):
        issues.append(issue("error", "duplicate", path, "중복 현지화 태그가 있습니다."))
    for index, tag in enumerate(value):
        tag_path = f"{path}[{index}]"
        if not _nonempty_string(tag):
            issues.append(issue("error", "required", tag_path, "빈 현지화 태그를 사용할 수 없습니다."))
        elif len(tag) > 40:
            issues.append(issue("error", "max_length", tag_path, "현지화 태그는 40자 이하여야 합니다."))


def _validate_deck_localizations(
    value: Any,
    *,
    base_tag_count: int | None,
    issues: list[dict[str, str]],
) -> None:
    path = "localizations"
    if not isinstance(value, dict):
        issues.append(issue("error", "type", path, "덱 현지화는 객체여야 합니다."))
        return
    if not value:
        issues.append(issue("error", "localization_count", path, "현지화가 하나 이상 필요합니다."))
    for language_code, localization in value.items():
        localization_path = f"{path}.{language_code}"
        if language_code not in SUPPORTED_LOCALIZATION_CODES:
            issues.append(issue("error", "unsupported_locale", localization_path, "지원하지 않는 콘텐츠 로케일입니다."))
        if not isinstance(localization, dict):
            issues.append(issue("error", "type", localization_path, "덱 현지화 값은 객체여야 합니다."))
            continue
        required_fields = {"name", "author_nickname", "tags"}
        for field in sorted(required_fields - set(localization)):
            issues.append(issue("error", "required", f"{localization_path}.{field}", "필수 값입니다."))
        for field in sorted(set(localization) - required_fields):
            issues.append(issue("error", "additional_property", f"{localization_path}.{field}", "지원하지 않는 필드입니다."))

        name = localization.get("name")
        if not _nonempty_string(name):
            issues.append(issue("error", "required", f"{localization_path}.name", "현지화 덱 이름이 필요합니다."))
        elif len(name) > 120:
            issues.append(issue("error", "max_length", f"{localization_path}.name", "현지화 덱 이름은 120자 이하여야 합니다."))
        nickname = localization.get("author_nickname")
        if not _nonempty_string(nickname):
            issues.append(issue("error", "required", f"{localization_path}.author_nickname", "현지화 작성자 이름이 필요합니다."))
        elif len(nickname) > 40:
            issues.append(issue("error", "max_length", f"{localization_path}.author_nickname", "현지화 작성자 이름은 40자 이하여야 합니다."))
        _validate_localized_tags(
            localization.get("tags"),
            path=f"{localization_path}.tags",
            base_tag_count=base_tag_count,
            issues=issues,
        )


def _validate_item_localizations(
    value: Any,
    *,
    path: str,
    issues: list[dict[str, str]],
) -> None:
    if not isinstance(value, dict):
        issues.append(issue("error", "type", path, "항목 현지화는 객체여야 합니다."))
        return
    if not value:
        issues.append(issue("error", "localization_count", path, "현지화가 하나 이상 필요합니다."))
    for language_code, localization in value.items():
        localization_path = f"{path}.{language_code}"
        if language_code not in SUPPORTED_LOCALIZATION_CODES:
            issues.append(issue("error", "unsupported_locale", localization_path, "지원하지 않는 콘텐츠 로케일입니다."))
        if not isinstance(localization, dict):
            issues.append(issue("error", "type", localization_path, "항목 현지화 값은 객체여야 합니다."))
            continue
        required_fields = {"meaning", "reading"}
        for field in sorted(required_fields - set(localization)):
            issues.append(issue("error", "required", f"{localization_path}.{field}", "필수 값입니다."))
        for field in sorted(set(localization) - required_fields):
            issues.append(issue("error", "additional_property", f"{localization_path}.{field}", "지원하지 않는 필드입니다."))
        for field, maximum in (("meaning", 500), ("reading", 300)):
            text = localization.get(field)
            field_path = f"{localization_path}.{field}"
            if not _nonempty_string(text):
                issues.append(issue("error", "required", field_path, "현지화 텍스트가 필요합니다."))
            elif len(text) > maximum:
                issues.append(issue("error", "max_length", field_path, f"현지화 텍스트는 {maximum}자 이하여야 합니다."))


def validate_deck(deck: Any) -> list[dict[str, str]]:
    """Mirror deck.schema.json and DeckValidator checks without dependencies."""
    issues: list[dict[str, str]] = []
    if not isinstance(deck, dict):
        return [issue("error", "type", "$", "덱 JSON은 객체여야 합니다.")]

    for field in sorted(DECK_FIELDS - set(deck)):
        issues.append(issue("error", "required", field, "필수 값입니다."))
    for field in sorted(set(deck) - DECK_FIELDS - OPTIONAL_DECK_FIELDS):
        issues.append(issue("error", "additional_property", field, "지원하지 않는 필드입니다."))

    deck_id = deck.get("deck_id")
    if not isinstance(deck_id, str) or not IDENTIFIER_PATTERN.fullmatch(deck_id):
        issues.append(issue("error", "identifier", "deck_id", "3~64자의 소문자 영문, 숫자, 밑줄, 하이픈만 사용할 수 있습니다."))

    version = deck.get("version")
    if not _is_int(version) or version < 1:
        issues.append(issue("error", "range", "version", "버전은 1 이상의 정수여야 합니다."))

    name = deck.get("name")
    if not _nonempty_string(name):
        issues.append(issue("error", "required", "name", "덱 이름을 입력해 주세요."))
    elif len(name) > 120:
        issues.append(issue("error", "max_length", "name", "덱 이름은 120자 이하여야 합니다."))

    author = deck.get("author")
    if not isinstance(author, dict):
        issues.append(issue("error", "type", "author", "작성자 정보가 필요합니다."))
    else:
        if set(author) - {"id", "nickname"}:
            issues.append(issue("error", "additional_property", "author", "작성자에는 id와 nickname만 사용할 수 있습니다."))
        author_id = author.get("id")
        if not isinstance(author_id, str) or not IDENTIFIER_PATTERN.fullmatch(author_id):
            issues.append(issue("error", "identifier", "author.id", "작성자 ID 형식이 올바르지 않습니다."))
        nickname = author.get("nickname")
        if not _nonempty_string(nickname):
            issues.append(issue("error", "required", "author.nickname", "작성자 이름을 입력해 주세요."))
        elif len(nickname) > 40:
            issues.append(issue("error", "max_length", "author.nickname", "작성자 이름은 40자 이하여야 합니다."))

    if not isinstance(deck.get("official"), bool):
        issues.append(issue("error", "type", "official", "공식 덱 여부는 true 또는 false여야 합니다."))
    if deck.get("type") not in {"word", "sentence"}:
        issues.append(issue("error", "enum", "type", "유형은 word 또는 sentence여야 합니다."))
    level = deck.get("level")
    if not _is_int(level) or not 1 <= level <= 3:
        issues.append(issue("error", "range", "level", "난이도는 1~3이어야 합니다."))

    tags = deck.get("tags")
    if not isinstance(tags, list):
        issues.append(issue("error", "type", "tags", "태그는 배열이어야 합니다."))
    else:
        if not 1 <= len(tags) <= 8:
            issues.append(issue("error", "tag_count", "tags", "태그는 1~8개여야 합니다."))
        if len(set(value for value in tags if isinstance(value, str))) != len(tags):
            issues.append(issue("error", "duplicate", "tags", "중복 태그가 있습니다."))
        for index, value in enumerate(tags):
            if not _nonempty_string(value):
                issues.append(issue("error", "required", f"tags[{index}]", "빈 태그를 사용할 수 없습니다."))
            elif len(value) > 40:
                issues.append(issue("error", "max_length", f"tags[{index}]", "태그는 40자 이하여야 합니다."))

    if "localizations" in deck:
        _validate_deck_localizations(
            deck.get("localizations"),
            base_tag_count=len(tags) if isinstance(tags, list) else None,
            issues=issues,
        )

    created_at = _parse_iso_date(deck.get("created_at"))
    updated_at = _parse_iso_date(deck.get("updated_at"))
    if created_at is None:
        issues.append(issue("error", "date_time", "created_at", "ISO 8601 날짜/시간이 필요합니다."))
    if updated_at is None:
        issues.append(issue("error", "date_time", "updated_at", "ISO 8601 날짜/시간이 필요합니다."))
    if created_at is not None and updated_at is not None and updated_at < created_at:
        issues.append(issue("error", "date_order", "updated_at", "수정일은 생성일보다 빠를 수 없습니다."))

    items = deck.get("items")
    if not isinstance(items, list):
        issues.append(issue("error", "type", "items", "학습 항목은 배열이어야 합니다."))
        return issues
    if not 1 <= len(items) <= 5000:
        issues.append(issue("error", "item_count", "items", "학습 항목은 1~5,000개여야 합니다."))

    ids: set[str] = set()
    korean_targets: Counter[str] = Counter()
    for index, item_value in enumerate(items):
        path = f"items[{index}]"
        if not isinstance(item_value, dict):
            issues.append(issue("error", "type", path, "항목은 객체여야 합니다."))
            continue
        for field in sorted(ITEM_FIELDS - set(item_value)):
            issues.append(issue("error", "required", f"{path}.{field}", "필수 값입니다."))
        for field in sorted(set(item_value) - ITEM_FIELDS - OPTIONAL_ITEM_FIELDS):
            issues.append(issue("error", "additional_property", f"{path}.{field}", "지원하지 않는 필드입니다."))

        item_id = item_value.get("id")
        if not isinstance(item_id, str) or not IDENTIFIER_PATTERN.fullmatch(item_id):
            issues.append(issue("error", "identifier", f"{path}.id", "항목 ID 형식이 올바르지 않습니다."))
        elif item_id in ids:
            issues.append(issue("error", "duplicate", f"{path}.id", "덱 안에서 중복된 항목 ID입니다."))
        else:
            ids.add(item_id)

        korean = item_value.get("ko")
        if not _nonempty_string(korean):
            issues.append(issue("error", "required", f"{path}.ko", "한국어를 입력해 주세요."))
        elif len(korean) > 10:
            issues.append(issue("error", "max_target_length", f"{path}.ko", "공백을 포함해 10자 이하여야 합니다."))
        else:
            supported, invalid_character = _is_supported_korean_target(korean)
            if invalid_character is not None:
                issues.append(issue("error", "undecomposable_ko", f"{path}.ko", f"조합 엔진이 ‘{invalid_character}’ 문자를 분해할 수 없습니다."))
            elif not supported:
                issues.append(issue("error", "missing_hangul", f"{path}.ko", "한글 음절 또는 자모가 하나 이상 필요합니다."))
            korean_targets[korean] += 1

        reading = item_value.get("reading_ja")
        if not _nonempty_string(reading):
            issues.append(issue("error", "required", f"{path}.reading_ja", "가타카나 읽기를 입력해 주세요."))
        elif len(reading) > 300:
            issues.append(issue("error", "max_length", f"{path}.reading_ja", "읽기는 300자 이하여야 합니다."))
        meaning = item_value.get("meaning_ja")
        if not _nonempty_string(meaning):
            issues.append(issue("error", "required", f"{path}.meaning_ja", "일본어 뜻을 입력해 주세요."))
        elif len(meaning) > 500:
            issues.append(issue("error", "max_length", f"{path}.meaning_ja", "뜻은 500자 이하여야 합니다."))

        if "localizations" in item_value:
            _validate_item_localizations(
                item_value.get("localizations"),
                path=f"{path}.localizations",
                issues=issues,
            )

        metadata = deck.get("localizations")
        required_locales = set(metadata) - {"ko"} if isinstance(metadata, dict) else set()
        item_localizations = item_value.get("localizations")
        for language in sorted(required_locales):
            if not isinstance(item_localizations, dict) or language not in item_localizations:
                issues.append(issue("error", "missing_localization", f"{path}.localizations.{language}",
                                    "공개된 메타데이터 언어의 뜻과 읽기가 필요합니다."))

        audio = item_value.get("audio")
        if audio is not None and not _nonempty_string(audio):
            issues.append(issue("error", "audio", f"{path}.audio", "오디오는 경로 문자열 또는 null이어야 합니다."))
        if audio is None:
            issues.append(issue("warning", "missing_audio", f"{path}.audio", "오프라인 음원이 없어 기기 TTS로 대체됩니다."))

    for korean, count in korean_targets.items():
        if korean and count > 1:
            issues.append(issue("warning", "duplicate_target", "items", f"‘{korean}’ 항목이 {count}번 중복됩니다."))
    return issues


def json_bytes(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def iso_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def tag_category(tag: str) -> str:
    purpose_tags = {
        "入門", "キーボード", "子音", "母音", "パッチム", "基礎単語", "韓国旅行",
        "TOPIK", "検定", "日常", "会話", "今どき", "K-POP", "Kドラマ", "恋愛",
        "デート", "友だち", "週末", "グルメ",
    }
    return "purpose" if tag in purpose_tags else "topic"


def _atomic_write(path: Path, payload: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    temporary.write_bytes(payload)
    os.replace(temporary, path)


def _catalog_preview_item(item: dict[str, Any]) -> dict[str, Any]:
    preview: dict[str, Any] = {"ko": item["ko"], "meaning_ja": item["meaning_ja"]}
    item_localizations = item.get("localizations")
    if isinstance(item_localizations, dict):
        preview_localizations = {
            language_code: {"meaning": localization["meaning"]}
            for language_code, localization in item_localizations.items()
            if language_code in SUPPORTED_LOCALIZATION_CODES
            and isinstance(localization, dict)
            and _nonempty_string(localization.get("meaning"))
        }
        if preview_localizations:
            preview["localizations"] = preview_localizations
    return preview


def _catalog_tags(
    entries: list[dict[str, Any]],
    existing_tags: Any,
) -> list[dict[str, Any]]:
    tag_counts: Counter[str] = Counter()
    tag_localizations: dict[str, dict[str, str]] = {}
    if isinstance(existing_tags, list):
        for existing_tag in existing_tags:
            if not isinstance(existing_tag, dict) or not isinstance(existing_tag.get("tag"), str):
                continue
            localizations = existing_tag.get("localizations")
            if isinstance(localizations, dict):
                tag_localizations[existing_tag["tag"]] = {
                    language_code: localized_tag
                    for language_code, localized_tag in localizations.items()
                    if language_code in SUPPORTED_LOCALIZATION_CODES and _nonempty_string(localized_tag)
                }

    for entry in entries:
        base_tags = entry.get("tags", [])
        if not isinstance(base_tags, list):
            continue
        tag_counts.update(base_tags)
        localizations = entry.get("localizations")
        if not isinstance(localizations, dict):
            continue
        for language_code, localization in localizations.items():
            if language_code not in SUPPORTED_LOCALIZATION_CODES or not isinstance(localization, dict):
                continue
            localized_tags = localization.get("tags")
            if not isinstance(localized_tags, list) or len(localized_tags) != len(base_tags):
                continue
            for base_tag, localized_tag in zip(base_tags, localized_tags):
                if isinstance(base_tag, str) and _nonempty_string(localized_tag):
                    tag_localizations.setdefault(base_tag, {})[language_code] = localized_tag

    result: list[dict[str, Any]] = []
    for tag, count in sorted(tag_counts.items(), key=lambda value: (-value[1], value[0].casefold())):
        catalog_tag: dict[str, Any] = {
            "tag": tag,
            "deck_count": count,
            "category": tag_category(tag),
        }
        localizations = tag_localizations.get(tag)
        if localizations:
            catalog_tag["localizations"] = localizations
        result.append(catalog_tag)
    return result


def _load_local_config(repository_root: Path) -> dict[str, str]:
    """Read optional ignored .env files without overriding process variables."""
    values: dict[str, str] = {}
    for path in (repository_root / ".env", repository_root / "tools" / "deck_backoffice" / ".env"):
        if not path.exists():
            continue
        for raw_line in path.read_text(encoding="utf-8").splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            key = key.strip()
            value = value.strip().strip("\"").strip("'")
            if key in {"OPENAI_API_KEY", "PIYOKEY_OPENAI_MODEL"}:
                values[key] = value
    return values


def _response_output_text(response: dict[str, Any]) -> str:
    texts: list[str] = []
    for output in response.get("output", []):
        if not isinstance(output, dict) or output.get("type") != "message":
            continue
        for content in output.get("content", []):
            if not isinstance(content, dict):
                continue
            if content.get("type") == "refusal":
                raise RequestError(HTTPStatus.UNPROCESSABLE_ENTITY, "AI가 이 항목 생성을 거절했습니다.")
            if content.get("type") == "output_text" and isinstance(content.get("text"), str):
                texts.append(content["text"])
    if not texts:
        raise RequestError(HTTPStatus.BAD_GATEWAY, "AI 응답에 사용할 수 있는 텍스트가 없습니다.")
    return "".join(texts)


class OpenAIContentService:
    """Generate Japanese learning metadata through stateless structured outputs."""

    def __init__(
        self,
        api_key: str,
        model: str = DEFAULT_OPENAI_MODEL,
        endpoint: str = DEFAULT_OPENAI_ENDPOINT,
        requester: Any = urllib.request.urlopen,
    ):
        self.api_key = api_key
        self.model = model
        self.endpoint = endpoint
        self.requester = requester

    @property
    def configured(self) -> bool:
        return bool(self.api_key)

    def generate(self, korean: str, *, deck_name: str = "", deck_type: str = "word") -> dict[str, str]:
        if not self.configured:
            raise RequestError(
                HTTPStatus.SERVICE_UNAVAILABLE,
                "OpenAI API 키가 없습니다. tools/deck_backoffice/.env에 OPENAI_API_KEY를 설정해 주세요.",
            )
        if not _nonempty_string(korean):
            raise RequestError(HTTPStatus.BAD_REQUEST, "AI로 채울 한국어를 입력해 주세요.")
        if len(korean) > 10:
            raise RequestError(HTTPStatus.UNPROCESSABLE_ENTITY, "한국어는 공백을 포함해 10자 이하여야 합니다.")
        supported, invalid_character = _is_supported_korean_target(korean)
        if invalid_character is not None or not supported:
            raise RequestError(HTTPStatus.UNPROCESSABLE_ENTITY, "한글 조합 엔진이 처리할 수 있는 한국어를 입력해 주세요.")

        schema = {
            "type": "object",
            "properties": {
                "reading_ja": {"type": "string", "minLength": 1, "maxLength": 300},
                "meaning_ja": {"type": "string", "minLength": 1, "maxLength": 500},
            },
            "required": ["reading_ja", "meaning_ja"],
            "additionalProperties": False,
        }
        request_body = {
            "model": self.model,
            "reasoning": {"effort": "none"},
            "store": False,
            "instructions": (
                "You create Japanese-facing metadata for a Korean typing-learning app. "
                "Return reading_ja as a natural katakana pronunciation guide for the exact Korean text. "
                "Return meaning_ja as a concise, natural Japanese meaning suitable for a vocabulary card. "
                "Preserve the Korean input exactly, infer the most common neutral meaning, do not add notes, "
                "parentheses, alternatives, romanization, or markdown."
            ),
            "input": json.dumps(
                {"ko": korean, "deck_name": deck_name, "deck_type": deck_type},
                ensure_ascii=False,
            ),
            "text": {
                "format": {
                    "type": "json_schema",
                    "name": "piyokey_deck_item",
                    "strict": True,
                    "schema": schema,
                }
            },
            "max_output_tokens": 300,
        }
        response = self._request(request_body)
        try:
            value = json.loads(_response_output_text(response))
        except json.JSONDecodeError as error:
            raise RequestError(HTTPStatus.BAD_GATEWAY, "AI 응답 JSON을 읽을 수 없습니다.") from error
        if not isinstance(value, dict) or not _nonempty_string(value.get("reading_ja")) or not _nonempty_string(value.get("meaning_ja")):
            raise RequestError(HTTPStatus.BAD_GATEWAY, "AI가 필수 텍스트를 모두 생성하지 못했습니다.")
        return {"reading_ja": value["reading_ja"].strip(), "meaning_ja": value["meaning_ja"].strip()}

    def _request(self, body: dict[str, Any]) -> dict[str, Any]:
        request = urllib.request.Request(
            self.endpoint,
            data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
            headers={
                "Authorization": f"Bearer {self.api_key}",
                "Content-Type": "application/json",
            },
            method="POST",
        )
        try:
            with self.requester(request, timeout=45) as response:
                value = json.loads(response.read())
        except urllib.error.HTTPError as error:
            message = "OpenAI API 요청에 실패했습니다."
            try:
                detail = json.loads(error.read()).get("error", {}).get("message")
            except (json.JSONDecodeError, AttributeError):
                detail = None
            if error.code == HTTPStatus.UNAUTHORIZED:
                message = "OpenAI API 키가 올바르지 않습니다."
            elif error.code == HTTPStatus.TOO_MANY_REQUESTS:
                message = "OpenAI API 사용 한도 또는 요청 속도를 확인해 주세요."
            elif detail:
                message = f"OpenAI API 오류: {detail}"
            raise RequestError(HTTPStatus.BAD_GATEWAY, message) from error
        except urllib.error.URLError as error:
            raise RequestError(HTTPStatus.BAD_GATEWAY, "OpenAI API에 연결할 수 없습니다. 네트워크를 확인해 주세요.") from error
        except (TimeoutError, json.JSONDecodeError) as error:
            raise RequestError(HTTPStatus.GATEWAY_TIMEOUT, "OpenAI API 응답을 제시간에 읽지 못했습니다.") from error
        if not isinstance(value, dict):
            raise RequestError(HTTPStatus.BAD_GATEWAY, "OpenAI API 응답 형식이 올바르지 않습니다.")
        if value.get("error"):
            raise RequestError(HTTPStatus.BAD_GATEWAY, "OpenAI API가 오류를 반환했습니다.")
        return value


class GTTSPronunciationService:
    """Create the same content-addressed gTTS MP3 used by the release pipeline."""

    def __init__(
        self,
        catalog_root: Path,
        *,
        attempts: int = GTTS_ATTEMPTS,
        generator_loader: Any = _load_gtts_generator,
        tts_factory: Any | None = None,
    ):
        self.catalog_root = catalog_root
        self.attempts = attempts
        self.generator_loader = generator_loader
        self.tts_factory = tts_factory
        self._generation_lock = threading.Lock()

    @property
    def installed_version(self) -> str | None:
        try:
            return importlib.metadata.version("gTTS")
        except importlib.metadata.PackageNotFoundError:
            return None

    @property
    def available(self) -> bool:
        return self.installed_version == REQUIRED_GTTS_VERSION

    @property
    def engine_label(self) -> str:
        return f"gTTS {REQUIRED_GTTS_VERSION} · {GTTS_LANGUAGE} · 보통 속도"

    def generate(self, korean: str) -> dict[str, Any]:
        if not _nonempty_string(korean):
            raise RequestError(HTTPStatus.BAD_REQUEST, "오디오를 만들 한국어를 입력해 주세요.")
        if not self.available:
            installed = self.installed_version
            detail = f"현재 {installed}" if installed else "현재 미설치"
            raise RequestError(
                HTTPStatus.SERVICE_UNAVAILABLE,
                f"gTTS {REQUIRED_GTTS_VERSION}가 필요합니다({detail}). "
                "python3 -m pip install -r tools/requirements-audio.txt 를 실행해 주세요.",
            )

        try:
            generator = self.generator_loader()
        except Exception as error:
            raise RequestError(
                HTTPStatus.INTERNAL_SERVER_ERROR,
                f"gTTS 생성기를 불러오지 못했습니다: {error}",
            ) from error

        relative = generator.audio_asset_path(korean)
        target = self.catalog_root / relative
        with self._generation_lock:
            if target.exists():
                try:
                    generator.validate_mp3(target)
                    return {
                        "audio": relative,
                        "generated": False,
                        "audio_engine": self.engine_label,
                    }
                except (OSError, RuntimeError):
                    # Keep the existing file until a validated replacement is ready.
                    pass

            try:
                generator.generate_gtts(
                    target,
                    korean,
                    lang=GTTS_LANGUAGE,
                    tld=GTTS_TLD,
                    slow=GTTS_SLOW,
                    attempts=self.attempts,
                    tts_factory=self.tts_factory,
                )
                generator.validate_mp3(target)
            except Exception as error:
                raise RequestError(
                    HTTPStatus.BAD_GATEWAY,
                    f"gTTS 한국어 MP3를 생성하지 못했습니다: {error}",
                ) from error

        return {
            "audio": relative,
            "generated": True,
            "audio_engine": self.engine_label,
        }


class DeckStore:
    def __init__(self, repository_root: Path = REPOSITORY_ROOT):
        self.repository_root = repository_root.resolve()
        self.catalog_root = self.repository_root / "shared" / "mock_catalog"
        self.decks_root = self.catalog_root / "decks"
        self.catalog_path = self.catalog_root / "catalog.json"
        self.override_root = self.repository_root / "shared" / "deck_overrides" / "main"

    def _safe_deck_path(self, relative_path: str) -> Path:
        pure = PurePosixPath(relative_path)
        if pure.is_absolute() or ".." in pure.parts or pure.suffix != ".json":
            raise RequestError(HTTPStatus.BAD_REQUEST, "안전하지 않은 덱 경로입니다.")
        candidate = (self.decks_root / Path(*pure.parts)).resolve()
        if self.decks_root.resolve() not in candidate.parents:
            raise RequestError(HTTPStatus.BAD_REQUEST, "덱 폴더 밖의 경로는 사용할 수 없습니다.")
        return candidate

    def load_catalog(self) -> dict[str, Any]:
        try:
            value = json.loads(self.catalog_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as error:
            raise RequestError(HTTPStatus.INTERNAL_SERVER_ERROR, f"catalog.json을 읽을 수 없습니다: {error}") from error
        if not isinstance(value, dict):
            raise RequestError(HTTPStatus.INTERNAL_SERVER_ERROR, "catalog.json 형식이 올바르지 않습니다.")
        return value

    def load_deck(self, relative_path: str) -> dict[str, Any]:
        path = self._safe_deck_path(relative_path)
        try:
            value = json.loads(path.read_text(encoding="utf-8"))
        except FileNotFoundError as error:
            raise RequestError(HTTPStatus.NOT_FOUND, "덱 파일을 찾을 수 없습니다.") from error
        except json.JSONDecodeError as error:
            raise RequestError(HTTPStatus.UNPROCESSABLE_ENTITY, f"JSON 문법 오류: {error}") from error
        if not isinstance(value, dict):
            raise RequestError(HTTPStatus.UNPROCESSABLE_ENTITY, "덱 JSON은 객체여야 합니다.")
        return value

    def list_decks(self) -> dict[str, Any]:
        catalog = self.load_catalog()
        catalog_by_path = {
            str(entry.get("file_url", "")).removeprefix("decks/"): entry
            for entry in catalog.get("decks", [])
            if isinstance(entry, dict)
        }
        grouped: dict[str, list[dict[str, Any]]] = {}
        for path in sorted(self.decks_root.rglob("*.json")):
            relative = path.relative_to(self.decks_root).as_posix()
            try:
                deck = json.loads(path.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                continue
            if not isinstance(deck, dict) or not isinstance(deck.get("deck_id"), str):
                continue
            grouped.setdefault(deck["deck_id"], []).append({"path": relative, "deck": deck})

        summaries: list[dict[str, Any]] = []
        for deck_id, versions in grouped.items():
            selected = next((value for value in versions if value["path"] in catalog_by_path), None)
            if selected is None:
                selected = max(
                    versions,
                    key=lambda value: value["deck"].get("version", 0)
                    if _is_int(value["deck"].get("version")) else 0,
                )
            deck = selected["deck"]
            entry = catalog_by_path.get(selected["path"])
            summaries.append({
                "path": selected["path"],
                "deck_id": deck_id,
                "version": deck.get("version", 0),
                "name": deck.get("name", deck_id),
                "type": deck.get("type", "word"),
                "level": deck.get("level", 1),
                "tags": deck.get("tags", []),
                "item_count": len(deck.get("items", [])) if isinstance(deck.get("items"), list) else 0,
                "folder": str(PurePosixPath(selected["path"]).parent).replace(".", ""),
                "catalog_visible": entry is not None,
                "featured": bool(entry and entry.get("featured")),
                "available_versions": sorted(
                    [value["deck"].get("version", 0) for value in versions if _is_int(value["deck"].get("version"))],
                    reverse=True,
                ),
            })
        summaries.sort(key=lambda value: (not value["catalog_visible"], value["folder"], str(value["name"]).casefold()))
        return {
            "catalog_version": catalog.get("catalog_version"),
            "generated_at": catalog.get("generated_at"),
            "decks": summaries,
            "allowed_folders": sorted(ALLOWED_FOLDERS),
        }

    def save(self, request: Any) -> dict[str, Any]:
        if not isinstance(request, dict) or not isinstance(request.get("deck"), dict):
            raise RequestError(HTTPStatus.BAD_REQUEST, "저장할 덱 데이터가 없습니다.")
        deck = json.loads(json.dumps(request["deck"], ensure_ascii=False))
        source_path = request.get("source_path") or ""
        folder = request.get("folder") or ""
        catalog_visible = bool(request.get("catalog_visible", False))
        featured = bool(request.get("featured", False))
        bump_version = bool(request.get("bump_version", False))

        if folder not in ALLOWED_FOLDERS:
            raise RequestError(HTTPStatus.BAD_REQUEST, "지원하지 않는 덱 분류입니다.")
        if catalog_visible and folder:
            raise RequestError(HTTPStatus.BAD_REQUEST, "카탈로그 덱은 일반 덱 폴더에 저장해야 합니다.")

        if source_path:
            source_deck = self.load_deck(source_path)
            if source_deck.get("deck_id") != deck.get("deck_id"):
                raise RequestError(HTTPStatus.CONFLICT, "기존 덱의 ID는 바꿀 수 없습니다. ‘복제하여 새 덱’을 사용해 주세요.")
        else:
            existing_ids = {entry["deck_id"] for entry in self.list_decks()["decks"]}
            if deck.get("deck_id") in existing_ids:
                raise RequestError(HTTPStatus.CONFLICT, "같은 덱 ID가 이미 있습니다.")

        if bump_version:
            current_version = deck.get("version")
            if _is_int(current_version):
                deck["version"] = current_version + 1
        deck["updated_at"] = iso_now()
        issues = validate_deck(deck)
        errors = [value for value in issues if value["severity"] == "error"]
        if errors:
            raise RequestError(HTTPStatus.UNPROCESSABLE_ENTITY, "오류를 고친 뒤 저장해 주세요.", issues)

        relative = PurePosixPath(folder) / f'{deck["deck_id"]}_v{deck["version"]}.json'
        relative_string = relative.as_posix()
        target = self._safe_deck_path(relative_string)
        if target.exists() and relative_string != source_path and not request.get("allow_overwrite", False):
            raise RequestError(HTTPStatus.CONFLICT, "같은 버전의 덱 파일이 이미 있습니다.")

        payload = json_bytes(deck)
        catalog = self.load_catalog()
        entries = catalog.setdefault("decks", [])
        existing_entry = next((entry for entry in entries if entry.get("deck_id") == deck["deck_id"]), None)
        catalog_changed = catalog_visible or existing_entry is not None
        if catalog_visible:
            new_entry = {
                "deck_id": deck["deck_id"],
                "version": deck["version"],
                "name": deck["name"],
                "author_nickname": deck["author"]["nickname"],
                "official": deck["official"],
                "featured": featured,
                "type": deck["type"],
                "level": deck["level"],
                "tags": deck["tags"],
                "item_count": len(deck["items"]),
                "size_bytes": len(payload),
                "downloads_total": existing_entry.get("downloads_total", 0) if existing_entry else 0,
                "downloads_7d": existing_entry.get("downloads_7d", 0) if existing_entry else 0,
                "created_at": deck["created_at"],
                "preview_items": [_catalog_preview_item(item) for item in deck["items"][:10]],
                "file_url": f"decks/{relative_string}",
            }
            if "localizations" in deck:
                new_entry["localizations"] = deck["localizations"]
            if existing_entry is None:
                entries.append(new_entry)
            else:
                existing_entry.clear()
                existing_entry.update(new_entry)
        elif existing_entry is not None:
            entries.remove(existing_entry)

        if catalog_changed:
            catalog["tags"] = _catalog_tags(entries, catalog.get("tags"))
            current_catalog_version = catalog.get("catalog_version", 0)
            catalog["catalog_version"] = current_catalog_version + 1 if _is_int(current_catalog_version) else 1
            catalog["generated_at"] = iso_now()

        override_path = self.override_root / Path(*PurePosixPath(folder).parts) / f'{deck["deck_id"]}.json'
        _atomic_write(target, payload)
        _atomic_write(override_path, payload)
        if catalog_changed:
            _atomic_write(self.catalog_path, json_bytes(catalog))

        return {
            "deck": deck,
            "path": relative_string,
            "catalog_version": catalog.get("catalog_version"),
            "issues": issues,
            "message": (
                "새 버전과 카탈로그를 저장했습니다."
                if bump_version and catalog_changed
                else "새 버전 덱을 저장했습니다."
                if bump_version
                else "덱과 카탈로그를 저장했습니다."
                if catalog_changed
                else "덱을 저장했습니다."
            ),
        }


class DeckBackofficeHandler(BaseHTTPRequestHandler):
    server_version = "PiyokeyDeckStudio/1.0"
    store: DeckStore
    content_service: OpenAIContentService
    audio_service: GTTSPronunciationService

    def do_GET(self) -> None:  # noqa: N802
        try:
            parsed = urlparse(self.path)
            if parsed.path == "/api/state":
                self._send_json(HTTPStatus.OK, self.store.list_decks())
                return
            if parsed.path == "/api/deck":
                relative_path = parse_qs(parsed.query).get("path", [""])[0]
                self._send_json(HTTPStatus.OK, {"deck": self.store.load_deck(relative_path)})
                return
            if parsed.path == "/api/ai/status":
                self._send_json(HTTPStatus.OK, {
                    "configured": self.content_service.configured,
                    "model": self.content_service.model,
                    "audio_available": self.audio_service.available,
                    "audio_engine": self.audio_service.engine_label,
                })
                return
            if parsed.path.startswith("/media/audio/"):
                self._send_audio(parsed.path.removeprefix("/media/"))
                return
            self._send_static(parsed.path)
        except RequestError as error:
            self._send_json(error.status, {"message": error.message, "issues": error.issues})
        except Exception as error:  # pragma: no cover - final local safety net
            self._send_json(HTTPStatus.INTERNAL_SERVER_ERROR, {"message": f"예상하지 못한 오류: {error}"})

    def do_POST(self) -> None:  # noqa: N802
        try:
            self._require_local_origin()
            parsed = urlparse(self.path)
            body = self._read_json()
            if parsed.path == "/api/validate":
                self._send_json(HTTPStatus.OK, {"issues": validate_deck(body.get("deck"))})
                return
            if parsed.path == "/api/save":
                self._send_json(HTTPStatus.OK, self.store.save(body))
                return
            if parsed.path == "/api/ai/enrich":
                korean = body.get("ko")
                generated = self.content_service.generate(
                    korean,
                    deck_name=body.get("deck_name") if isinstance(body.get("deck_name"), str) else "",
                    deck_type=body.get("deck_type") if body.get("deck_type") in {"word", "sentence"} else "word",
                )
                warnings: list[str] = []
                audio_result: dict[str, Any] = {
                    "audio": None,
                    "generated": False,
                    "audio_engine": self.audio_service.engine_label,
                }
                if body.get("generate_audio", True):
                    try:
                        audio_result = self.audio_service.generate(korean)
                    except RequestError as error:
                        warnings.append(error.message)
                self._send_json(HTTPStatus.OK, {
                    **generated,
                    **audio_result,
                    "model": self.content_service.model,
                    "warnings": warnings,
                })
                return
            if parsed.path == "/api/audio/generate":
                self._send_json(HTTPStatus.OK, self.audio_service.generate(body.get("ko")))
                return
            raise RequestError(HTTPStatus.NOT_FOUND, "API를 찾을 수 없습니다.")
        except RequestError as error:
            self._send_json(error.status, {"message": error.message, "issues": error.issues})
        except Exception as error:  # pragma: no cover - final local safety net
            self._send_json(HTTPStatus.INTERNAL_SERVER_ERROR, {"message": f"예상하지 못한 오류: {error}"})

    def _require_local_origin(self) -> None:
        origin = self.headers.get("Origin")
        if origin and urlparse(origin).hostname not in {"127.0.0.1", "localhost"}:
            raise RequestError(HTTPStatus.FORBIDDEN, "로컬 편집기에서 보낸 요청만 허용됩니다.")

    def _read_json(self) -> dict[str, Any]:
        content_type = self.headers.get("Content-Type", "")
        if "application/json" not in content_type:
            raise RequestError(HTTPStatus.UNSUPPORTED_MEDIA_TYPE, "JSON 요청만 허용됩니다.")
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError as error:
            raise RequestError(HTTPStatus.BAD_REQUEST, "Content-Length가 올바르지 않습니다.") from error
        if length <= 0 or length > 8 * 1024 * 1024:
            raise RequestError(HTTPStatus.REQUEST_ENTITY_TOO_LARGE, "요청 크기는 8MB 이하여야 합니다.")
        try:
            value = json.loads(self.rfile.read(length))
        except json.JSONDecodeError as error:
            raise RequestError(HTTPStatus.BAD_REQUEST, f"JSON 문법 오류: {error}") from error
        if not isinstance(value, dict):
            raise RequestError(HTTPStatus.BAD_REQUEST, "요청 본문은 JSON 객체여야 합니다.")
        return value

    def _send_static(self, path: str) -> None:
        routes = {
            "/": ("index.html", "text/html; charset=utf-8"),
            "/index.html": ("index.html", "text/html; charset=utf-8"),
            "/app.js": ("app.js", "text/javascript; charset=utf-8"),
            "/styles.css": ("styles.css", "text/css; charset=utf-8"),
        }
        if path not in routes:
            self.send_error(HTTPStatus.NOT_FOUND)
            return
        file_name, content_type = routes[path]
        payload = (STATIC_ROOT / file_name).read_bytes()
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(payload)

    def _send_audio(self, relative_path: str) -> None:
        pure = PurePosixPath(relative_path)
        if (
            pure.is_absolute()
            or ".." in pure.parts
            or len(pure.parts) != 2
            or pure.parts[0] != "audio"
            or pure.suffix not in {".mp3", ".caf"}
        ):
            raise RequestError(HTTPStatus.BAD_REQUEST, "안전하지 않은 오디오 경로입니다.")
        path = (self.store.catalog_root / Path(*pure.parts)).resolve()
        audio_root = (self.store.catalog_root / "audio").resolve()
        if audio_root not in path.parents or not path.exists():
            raise RequestError(HTTPStatus.NOT_FOUND, "오디오 파일을 찾을 수 없습니다.")
        payload = path.read_bytes()
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", "audio/mpeg" if pure.suffix == ".mp3" else "audio/x-caf")
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(payload)

    def _send_json(self, status: int, value: Any) -> None:
        payload = json.dumps(value, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, format: str, *args: Any) -> None:
        return


def make_server(
    host: str,
    port: int,
    store: DeckStore,
    content_service: OpenAIContentService | None = None,
    audio_service: GTTSPronunciationService | None = None,
) -> ThreadingHTTPServer:
    local_config = _load_local_config(store.repository_root)
    content_service = content_service or OpenAIContentService(
        api_key=os.environ.get("OPENAI_API_KEY", local_config.get("OPENAI_API_KEY", "")),
        model=os.environ.get(
            "PIYOKEY_OPENAI_MODEL",
            local_config.get("PIYOKEY_OPENAI_MODEL", DEFAULT_OPENAI_MODEL),
        ),
    )
    audio_service = audio_service or GTTSPronunciationService(store.catalog_root)
    handler = type(
        "ConfiguredDeckBackofficeHandler",
        (DeckBackofficeHandler,),
        {"store": store, "content_service": content_service, "audio_service": audio_service},
    )
    return ThreadingHTTPServer((host, port), handler)


def main() -> None:
    parser = argparse.ArgumentParser(description="Run the local PIYOKEY deck backoffice.")
    parser.add_argument("--host", default="127.0.0.1", choices=["127.0.0.1", "localhost"])
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--no-open", action="store_true", help="Do not open the browser automatically.")
    args = parser.parse_args()

    server = make_server(args.host, args.port, DeckStore())
    actual_port = server.server_address[1]
    url = f"http://127.0.0.1:{actual_port}"
    print(f"PIYOKEY Deck Studio: {url}")
    print("종료하려면 Ctrl+C를 누르세요.")
    if not args.no_open:
        threading.Timer(0.35, lambda: webbrowser.open(url)).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nDeck Studio를 종료했습니다.")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
