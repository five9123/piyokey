# Valid `.typedeck` fixture cases

- `basic-deck.json` and `basic-manifest.json` are reviewable, pretty-printed
  source entries.
- `basic.typedeck` is the canonical writer golden. Python, Swift, and Kotlin
  writers must reproduce it byte-for-byte.
- `pretty-basic.typedeck` contains the reviewable source entries unchanged. It
  proves that readers accept valid whitespace and key ordering, while writers
  still normalize output to `basic.typedeck`.
- `multilingual-deck.json` and `multilingual.typedeck` are the deck-schema-v2
  source and canonical writer golden. They declare `default_locale: ar` and the
  complete content locale set `ar` + `zh-Hant`; Python, Swift, and Kotlin
  writers reproduce the package byte-for-byte.

Run `python3 tools/gen_piyodeck_fixtures.py` to regenerate all binary fixtures
and `../cases.json` deterministically.
