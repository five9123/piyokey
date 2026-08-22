#!/usr/bin/env python3
"""Generate shared/test_vectors.json for the Hangul composition engine.

Decomposes target strings into compatibility jamo key sequences (two-beolsik),
programmatically verified against Unicode Hangul syllable arithmetic.
Both iOS and Android test suites must consume the generated JSON.
"""
import json, sys, unicodedata
from pathlib import Path

CHO = ["ㄱ","ㄲ","ㄴ","ㄷ","ㄸ","ㄹ","ㅁ","ㅂ","ㅃ","ㅅ","ㅆ","ㅇ","ㅈ","ㅉ","ㅊ","ㅋ","ㅌ","ㅍ","ㅎ"]
JUNG = ["ㅏ","ㅐ","ㅑ","ㅒ","ㅓ","ㅔ","ㅕ","ㅖ","ㅗ","ㅘ","ㅙ","ㅚ","ㅛ","ㅜ","ㅝ","ㅞ","ㅟ","ㅠ","ㅡ","ㅢ","ㅣ"]
JONG = ["","ㄱ","ㄲ","ㄳ","ㄴ","ㄵ","ㄶ","ㄷ","ㄹ","ㄺ","ㄻ","ㄼ","ㄽ","ㄾ","ㄿ","ㅀ","ㅁ","ㅂ","ㅄ","ㅅ","ㅆ","ㅇ","ㅈ","ㅊ","ㅋ","ㅌ","ㅍ","ㅎ"]

# keys typed on a two-beolsik keyboard to produce each compound jamo
COMPOUND_JUNG = {"ㅘ":["ㅗ","ㅏ"],"ㅙ":["ㅗ","ㅐ"],"ㅚ":["ㅗ","ㅣ"],"ㅝ":["ㅜ","ㅓ"],"ㅞ":["ㅜ","ㅔ"],"ㅟ":["ㅜ","ㅣ"],"ㅢ":["ㅡ","ㅣ"]}
COMPOUND_JONG = {"ㄳ":["ㄱ","ㅅ"],"ㄵ":["ㄴ","ㅈ"],"ㄶ":["ㄴ","ㅎ"],"ㄺ":["ㄹ","ㄱ"],"ㄻ":["ㄹ","ㅁ"],"ㄼ":["ㄹ","ㅂ"],"ㄽ":["ㄹ","ㅅ"],"ㄾ":["ㄹ","ㅌ"],"ㄿ":["ㄹ","ㅍ"],"ㅀ":["ㄹ","ㅎ"],"ㅄ":["ㅂ","ㅅ"]}
SHIFT_JAMO = {"ㄲ","ㄸ","ㅃ","ㅆ","ㅉ","ㅒ","ㅖ"}

def decompose_syllable(ch):
    code = ord(ch)
    if not (0xAC00 <= code <= 0xD7A3):
        return None
    s = code - 0xAC00
    return CHO[s // (21*28)], JUNG[(s % (21*28)) // 28], JONG[s % 28]

def key_sequence(text):
    keys = []
    for ch in text:
        d = decompose_syllable(ch)
        if d is None:
            keys.append(ch)  # space, punctuation, bare jamo
            continue
        cho, jung, jong = d
        keys.append(cho)
        keys.extend(COMPOUND_JUNG.get(jung, [jung]))
        if jong:
            keys.extend(COMPOUND_JONG.get(jong, [jong]))
    return keys

def verify(text, keys):
    """Re-compose keys with a reference automaton and assert equality."""
    # minimal reference automaton (mirrors PRD §6.2)
    out, cho, jung, jong = [], None, None, None
    def flush():
        nonlocal cho, jung, jong
        if cho is None and jung is None: return
        if cho is not None and jung is not None:
            j = JONG.index(jong) if jong else 0
            out.append(chr(0xAC00 + (CHO.index(cho)*21 + JUNG.index(jung))*28 + j))
        elif cho is not None:
            out.append(cho)
        else:
            out.append(jung)
        cho = jung = jong = None
    rev_cj = {tuple(v): k for k, v in COMPOUND_JUNG.items()}
    rev_cg = {tuple(v): k for k, v in COMPOUND_JONG.items()}
    for k in keys:
        if k not in CHO and k not in JUNG:
            flush(); out.append(k); continue
        is_vowel = k in JUNG
        if not is_vowel:
            if cho is None and jung is None:
                cho = k
            elif jung is None:
                flush(); cho = k
            else:
                if jong is None:
                    if k in JONG:
                        jong = k
                    else:
                        flush(); cho = k
                elif (jong, k) in rev_cg:
                    jong = rev_cg[(jong, k)]
                else:
                    flush(); cho = k
        else:
            if cho is not None and jung is None:
                jung = k
            elif jung is not None and jong is None and (jung, k) in rev_cj:
                jung = rev_cj[(jung, k)]
            elif jong is not None:
                # carry-over (dokkaebi): last jong element moves to next syllable
                carried = None
                for base, parts in COMPOUND_JONG.items():
                    if jong == base:
                        jong, carried = parts[0], parts[1]
                        break
                if carried is None:
                    carried, jong = jong, None
                flush(); cho, jung = carried, k
            elif cho is None and jung is None:
                jung = k
            else:
                flush(); jung = k
    flush()
    composed = "".join(out)
    assert composed == text, f"verify failed: {text!r} -> {keys} -> {composed!r}"

CASES = [
    ("greeting", "안녕하세요"),
    ("fandom_love", "사랑해요"),
    ("compound_jong_dark", "닭"),
    ("compound_jong_bs", "없다"),
    ("carryover_dokkaebi", "달가"),
    ("carryover_from_dark", "닭이"),
    ("compound_vowel_oe", "외국"),
    ("compound_vowel_ui", "의자"),
    ("compound_vowel_wa", "과자"),
    ("shift_ssang", "꿀"),
    ("shift_mixed", "빨리"),
    ("sentence_with_space", "한국 최고"),
    ("fan_comment", "오빠 사랑해"),
    ("perfect_case", "괜찮아요"),
    ("best_case", "최고예요"),
]

def main():
    vectors = []
    for name, text in CASES:
        keys = key_sequence(text)
        verify(text, keys)
        vectors.append({
            "name": name,
            "target": text,
            "key_sequence": keys,
            "uses_shift": [k for k in keys if k in SHIFT_JAMO],
            "nfd_length": len(unicodedata.normalize("NFD", text)),
        })
    backspace_cases = [
        {"name": "bs_disassemble_gan", "type_keys": ["ㄱ","ㅏ","ㄴ"], "then_backspaces": 1, "expected": "가"},
        {"name": "bs_disassemble_to_cho", "type_keys": ["ㄱ","ㅏ","ㄴ"], "then_backspaces": 2, "expected": "ㄱ"},
        {"name": "bs_disassemble_all", "type_keys": ["ㄱ","ㅏ","ㄴ"], "then_backspaces": 3, "expected": ""},
        {"name": "bs_compound_jong", "type_keys": ["ㄷ","ㅏ","ㄹ","ㄱ"], "then_backspaces": 1, "expected": "달"},
        {"name": "bs_compound_vowel", "type_keys": ["ㅇ","ㅗ","ㅣ"], "then_backspaces": 1, "expected": "오"},
        {"name": "bs_across_syllables", "type_keys": ["ㄱ","ㅏ","ㄴ","ㅏ"], "then_backspaces": 1, "expected": "간"},
        {"name": "bs_compound_jong_to_syllable", "type_keys": ["ㄷ","ㅏ","ㄹ","ㄱ"], "then_backspaces": 2, "expected": "다"},
        {"name": "bs_compound_vowel_to_cho", "type_keys": ["ㅇ","ㅗ","ㅣ"], "then_backspaces": 2, "expected": "ㅇ"},
        {"name": "bs_restore_before_carryover", "type_keys": ["ㄷ","ㅏ","ㄹ","ㄱ","ㅏ"], "then_backspaces": 1, "expected": "닭"},
        {"name": "bs_ignores_excess_deletes", "type_keys": ["ㄱ","ㅏ"], "then_backspaces": 4, "expected": ""},
    ]
    out = {
        "version": 1,
        "description": "Shared test vectors for the two-beolsik Hangul composition engine. Both platforms must pass all cases. See PRD section 6.",
        "composition_cases": vectors,
        "backspace_cases": backspace_cases,
    }
    dest = Path(__file__).resolve().parent.parent / "shared" / "test_vectors.json"
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"wrote {dest} ({len(vectors)} composition + {len(backspace_cases)} backspace cases)")

if __name__ == "__main__":
    main()
