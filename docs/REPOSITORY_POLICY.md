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

## Verification and merge gate

GitHub Actions is active. Pull requests run only the workflows selected by the
native path filters under `.github/workflows/`. Python content/release contracts
absorb the former standalone pronunciation workflow; Swift, iOS, and Android
each have an independently scoped workflow. Pure documentation changes do not
start a platform build, while `release/**` keeps the fast Python contract and
`shared/**` keeps Python plus both mobile consumers.

`Scheduled platform regression` runs every Monday at 03:00 JST and can also be
started manually. It runs iOS unit tests on the pinned simulator and Android
instrumentation tests on an API 35 emulator. A failed or incomplete scheduled
run closes the release gate until the failure is explained or a succeeding run
covers the same source.

Workflow dependencies use full commit SHAs with a nearby reviewed release tag
comment. Repository settings allow GitHub-owned actions only and require SHA
pinning. Dependabot vulnerability alerts and automated security updates are
enabled; `.github/dependabot.yml` schedules version-update pull requests for
GitHub Actions, Android Gradle, and the standalone web analytics package. A
Dependabot pull request follows the same test and merge gate as any other PR.

The repository is private, and the current GitHub plan does not expose branch
protection or repository rulesets for it. Until the account owner explicitly
approves a plan change, GitHub cannot technically prevent an early merge. The
maintainer therefore applies this fail-closed manual gate:

- wait until the pull request has no pending or in-progress checks;
- require every displayed, applicable check to complete successfully;
- treat failure, cancellation, timeout, or an unexplained skip as blocking;
- treat a workflow omitted by its documented path filter as not applicable,
  not as a successful check;
- verify that the head commit has not changed after the successful runs; and
- squash merge only after those conditions are recorded in the pull request.

An emergency exception requires explicit user approval recorded in that pull
request. A past exception is not reusable and never waives device, store,
account, signing, or external-service gates.

Every executable change still requires an issue, an issue-owned branch, and a
pull request. Before requesting verification, the primary agent must run the
smallest relevant local test set described by `AGENTS.md` and record all of the
following in the issue or pull request:

- the exact commands that were run and their results;
- the commit that the results cover;
- tests or manual gates that were not run and why;
- any device, store, account, signing, or external-service gate still open.

Local results are evidence for the tested commit only. They do not replace the
successful GitHub checks or required user approval, real-device checks, store
review, service-console verification, or release signing. A pull request stays
in `Verify` until its applicable external gates are resolved.

If GitHub Pro is later approved, enable required status checks, required
conversation resolution, blocked force pushes and deletions, and pull-request
only changes on `main`. Do not change repository visibility, billing, or paid
overage implicitly.

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
