# PIYOKEY repository policy

## Source layout

PIYOKEY uses one private source monorepo. The existing `ios/` tree remains in
place, the active Kotlin/Compose port lives under `android/`, and a future web
port will live under `web/`. Cross-platform contracts live under `shared/`;
platform UI, storage, audio, input, and purchase integrations stay in their
platform trees.

The Swift, Kotlin, and TypeScript Hangul engines are independent pure-function
implementations. They must consume the same schemas, fixtures, and
`shared/test_vectors.json` rather than sharing a platform runtime.

## Files kept in the source repository

- platform source, tests, and project configuration;
- `PRD.md`, `DECISIONS.md`, `AGENTS.md`, and collaboration documentation;
- schemas, test vectors, catalog fixtures, content localization, and tools;
- source-controlled app assets and the shared brand source;
- offline pronunciation MP3 files required to reproduce an app bundle;
- current release metadata and the current App Store screenshot source/final
  set required by strict release preflight.

The pronunciation MP3 set is intentionally stored as ordinary Git objects. The
files are individually small, content-addressed, and required for an offline,
reproducible build.

## Files kept outside the source repository

- Xcode archives, test results, dSYMs, IPAs, provisioning profiles, Instruments
  traces, system logs, and exported upload directories;
- milestone screenshots and recordings, historical Notion screenshots, and
  third-party visual references;
- App Preview source clips, intermediate renders, and upload-ready videos;
- packaged screenshot ZIP files and other reproducible release bundles.

Durable evidence belongs in a private `piyokey-release-assets` archive or a
versioned release attachment. The source repository keeps a small text manifest
with version, checksum, provenance, and retrieval location when an external
artifact is release-critical. GitHub Actions artifacts are temporary CI output,
not the durable release archive.

## Large-file policy

The source repository does not use Git LFS by default. A new file larger than
10 MB requires an explicit repository-policy review. Essential binary source
assets may use LFS only after the clone, quota, and release-archive impact is
accepted; generated output is moved outside the source repository instead.

## Collaboration boundary

- One issue owns one scoped branch and one primary agent.
- Every agent uses an independent clone or worktree.
- No agent pushes directly to `main`.
- Pull requests identify the related PRD section, acceptance criteria, touched
  shared contracts, tests run, and tests not run.
- Changes under `shared/` require all affected platform contract tests.
- `project.pbxproj`, `PRD.md`, `DECISIONS.md`, catalog indexes, localization
  files, and generated audio/content are serialized when concurrent tasks would
  touch the same file.
- Release signing and store publication credentials are never provided to a
  general development agent.
