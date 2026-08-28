# Invalid `.typedeck` fixture cases

Shared binary golden cases are enumerated with exact sizes, SHA-256 values, and
coarse expected error families in `../cases.json`. They cover manifest digest,
Unicode/BOM/duplicate JSON keys, future versions, encryption, data descriptors,
compression, ZIP64, local/central header mismatch, CRC, unsafe paths, and hidden
preamble failures.

Additional container corruption cases are generated in platform tests so local
and central ZIP records can be mutated independently. Reviewable source JSON
fixtures cover user-content policy failures:

- `official-deck.json`: attempts to claim a reserved first-party ID and badge.
- `audio-deck.json`: embeds a remote audio URL, which v1 user packages forbid.
- `unknown-field-deck.json`: carries an entitlement-like field rejected by the
  shared deck schema's `additionalProperties: false` rule.

Run `python3 tools/gen_piyodeck_fixtures.py` to regenerate the shared binary
fixtures and `../cases.json` deterministically.
