# PIYOKEY Deck Package (`.piyodeck`) v1

Status: target specification for PIYOKEY 1.1.

`.piyodeck` is a portable, offline document containing exactly one user-created
PIYOKEY deck. Importing and playing a valid document does not convey or require
any purchase entitlement. The document is data only: it cannot contain code,
remote resources, account identifiers, or payment state.

## 1. File identity

| Property | v1 value |
| --- | --- |
| Extension | `.piyodeck` |
| MIME type | `application/vnd.piyokey.deck+zip` |
| Apple UTType identifier | `app.piyokey.piyodeck` |
| Container | ZIP, STORE method only |
| Text encoding | UTF-8 without BOM |
| Decks per document | exactly one |

The archive has exactly these two regular-file entries, in this order when
written by PIYOKEY:

```text
manifest.json
deck.json
```

No directories, preamble, gaps, trailing data, archive comments, entry
comments, extra fields, or duplicate paths are permitted. Both local and
central ZIP headers must describe the same ASCII path, CRC-32, size, flags, and
STORE compression method. The only permitted general-purpose flag is the UTF-8
name flag (`0x0800`).

ZIP64, encryption, data descriptors, split archives, nested archives, symbolic
links, and non-STORE compression methods are not part of v1 and must be
rejected. A conforming writer uses the DOS timestamp `1980-01-01 00:00:00`, so
the same deck produces deterministic package bytes.

## 2. Limits

Limits are checked before decoding or allocating based on archive metadata.

| Resource | Maximum |
| --- | ---: |
| Complete package | 8 MiB (8,388,608 bytes) |
| `manifest.json` | 16 KiB (16,384 bytes) |
| `deck.json` | 4 MiB (4,194,304 bytes) |
| Deck items | 1,000 |

## 3. `manifest.json`

The normative JSON Schema is
[`../schema/piyodeck-manifest-v1.schema.json`](../schema/piyodeck-manifest-v1.schema.json).
Unknown fields and duplicate JSON object keys are invalid.

```json
{
  "format": "piyokey.deck-package",
  "format_version": 1,
  "deck_schema_version": 1,
  "deck": {
    "path": "deck.json",
    "media_type": "application/json",
    "deck_id": "user_550e8400e29b41d4a716446655440000",
    "deck_version": 3,
    "item_count": 120,
    "size_bytes": 48231,
    "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
  }
}
```

The version fields are independent:

- `format_version` changes when the package/container contract changes.
- `deck_schema_version` changes when the `deck.json` data contract changes.
- `deck.deck_version` is copied from `deck.json.version` and changes when that
  deck's content changes.

`deck_id`, `deck_version`, `item_count`, and the UTF-8 byte size must exactly
match the decoded `deck.json`. `sha256` is the lower-case SHA-256 digest of the
exact `deck.json` entry bytes. It detects damage; it is not a signature, DRM, or
proof of authorship.

## 4. `deck.json`

`deck.json` uses [`../schema/deck.schema.json`](../schema/deck.schema.json) and
the existing DeckKit semantic validator, with these stricter user-document
rules:

- `deck_id` is `user_` followed by 32 lower-case hexadecimal characters.
- Every item `id` is `item_` followed by 32 lower-case hexadecimal characters.
- IDs reserved for first-party content, including the `official_` prefix, are
  forbidden.
- `official` is exactly `false`.
- `items` contains 1 through 1,000 entries.
- Every `items[*].audio` is exactly `null`. If the same Korean target already has
  an app-bundled pronunciation MP3, the app may reuse that offline asset by its
  content hash; otherwise v1 uses on-device TTS and never uploads private deck
  text to generate speech.
- The existing schema's `additionalProperties: false` rules remain in force.
- Duplicate JSON object keys are invalid.
- `created_at` and `updated_at` use the canonical UTC, whole-second form
  `YYYY-MM-DDTHH:MM:SSZ`. Offsets and fractional seconds are rejected so every
  conforming writer emits identical timestamp bytes.

In particular, executable code, HTML behavior, plug-ins, remote URLs, purchase
receipts, license keys, premium flags, expiry fields, account email, and device
identifiers have no valid field in the document.

## 5. Reader validation order

A reader must fail closed, without changing an installed deck, in this order:

1. Enforce total package size.
2. Validate the EOCD and central-directory topology.
3. Validate entry set, paths, flags, methods, attributes, and size limits.
4. Cross-check each local header against its central header.
5. Verify each entry's ZIP CRC-32.
6. Reject invalid UTF-8, BOMs, and duplicate JSON object keys.
7. Validate and decode the manifest; reject unsupported versions.
8. Verify `deck.json` SHA-256 and manifest metadata.
9. Validate `deck.json` against the shared schema and DeckKit semantics.
10. Apply the stricter user-deck rules above.

Errors must distinguish unsupported future versions from damaged or unsafe
documents so the app can present an “update PIYOKEY” message when appropriate.

## 6. Compatibility and updates

A v1 reader accepts only `format_version: 1` and `deck_schema_version: 1`.
Unknown future versions are preserved by the caller if desired but are not
silently downgraded.

Editing a deck keeps its `deck_id` and stable item IDs, increments
`deck.json.version`, and updates `updated_at`. Importers use those stable IDs to
preserve deck and review progress. A writer must never place entitlement data
in a package; purchase restoration belongs to StoreKit and app-local state.

## 7. Developer tooling

The standard-library-only helper can create, inspect, and validate v1 packages
from the repository root:

```sh
python3 tools/piyodeck_tool.py pack --deck shared/piyodeck/fixtures/valid/basic-deck.json --output /tmp/basic.piyodeck
python3 tools/piyodeck_tool.py inspect /tmp/basic.piyodeck
python3 tools/piyodeck_tool.py validate /tmp/basic.piyodeck --deck-schema shared/schema/deck.schema.json
```

`pack` automatically validates the input against
`shared/schema/deck.schema.json` and the stricter user-deck rules in §4 before
writing a deterministic package. `inspect` and `validate` apply the same strict
v1 container and content checks; `validate` accepts an explicit deck schema so
CI and compatibility checks can pin the intended schema revision.

`fixtures/valid/basic.piyodeck` is the canonical cross-platform binary golden.
Python, Swift, and Kotlin writers must reproduce it byte-for-byte, and all three
readers must accept it using the pinned shared deck schema.

`fixtures/valid/pretty-basic.piyodeck` preserves valid pretty-printed entry
bytes and must be accepted by all readers. `fixtures/cases.json` records the
portable malicious corpus with exact size, SHA-256, and coarse error family.
Python and Swift execute every listed binary directly; Kotlin directly executes
the shared SHA and Unicode cases and covers the remaining recorded ZIP/JSON
families with deterministic in-memory mutations. Regenerate the binary corpus
with `python3 tools/gen_piyodeck_fixtures.py`.
