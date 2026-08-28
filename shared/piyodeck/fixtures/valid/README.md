# Valid `.typedeck` fixture cases

- `basic-deck.json` and `basic-manifest.json` are reviewable, pretty-printed
  source entries.
- `basic.typedeck` is the canonical writer golden. Python, Swift, and Kotlin
  writers must reproduce it byte-for-byte.
- `pretty-basic.typedeck` contains the reviewable source entries unchanged. It
  proves that readers accept valid whitespace and key ordering, while writers
  still normalize output to `basic.typedeck`.

Run `python3 tools/gen_piyodeck_fixtures.py` to regenerate all binary fixtures
and `../cases.json` deterministically.
