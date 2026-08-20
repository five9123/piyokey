# Invalid `.piyodeck` fixture cases

Binary corruption cases are generated in `PiyoDeckPackageTests` so their local
and central ZIP records can be mutated independently. The source JSON fixtures
remain reviewable text:

- `official-deck.json`: attempts to claim a reserved first-party ID and badge.
- `audio-deck.json`: embeds a remote audio URL, which v1 user packages forbid.
- `unknown-field-deck.json`: carries an entitlement-like field rejected by the
  shared deck schema's `additionalProperties: false` rule.

