# iOS 1.1 (7) — archive / TestFlight record

Issue #75. Updated 2026-08-29 JST. This is not an App Review submission or public-release record.

## Source and build identity

| Field | Verified value |
|---|---|
| Main merge | PR #74, `9af01ef96e06472eb3d842880649b5e697c5877e` |
| Archive checkout | `4e7acf242d11b5cfbbfe418adbc817c1f14e7179` |
| Identical complete Git tree | `3d9bb0354e83f0499e993dbe6bd74a0d2fdc0e2c` |
| App / version / build | `app.piyokey.Piyokey` / `1.1` / `7` |
| Apple team | `X44BQNTAH9` |
| Device families / minimum OS | iPhone + iPad / iOS 16.0 |
| Included UI localizations | ja, en, es, de, fr |
| Remote catalog URL | Empty; bundled content remains the baseline. No remote catalog was published. |

The squash merge changed commit identity, not the tree. `git fetch --prune origin` and `python3.12 tools/workspace_doctor.py --strict --require-origin-main` passed before archive, local export, and upload. The original dirty workspace and other working clones were not changed.

## Artifacts and verification

Artifacts are local, outside the release-record worktree and not committed:

`/Users/jungminoh/Documents/hanco/outputs/appstore-1.1-7-20260829/`

- `PIYOKEY-1.1-7.xcarchive`: Release archive, `ARCHIVE SUCCEEDED`.
- `export/typee.ipa`: 16,232,816 bytes, App Store Connect local export, `EXPORT SUCCEEDED`.
- `artifact-manifest.json`: source identities and SHA-256 of every archive file.
- `archive.log`, `export.log`, `upload.log`: exact command outputs, retained locally.

Archive ZIP SHA-256 (`PIYOKEY-1.1-7.xcarchive.zip`): `bbf814350c432795989bdf7cf9ded9d555936c4defc19e11faa7541cc694dd27`.

Local IPA SHA-256: `6a676f13db48c53da9234c878943a905f73002609152a50eed14f6759ea22221`.

Final archive tree SHA-256: `cbfdab353ac437b5379832df8a78c815c6dd1f01b7aef7a0449323d632f936b6`.

Tree hash means SHA-256 of UTF-8 lines sorted by relative path: `<file SHA-256><two spaces><relative POSIX path><LF>`. It is not the checksum of a ZIP file. Xcode appended distribution history to archive-level `Info.plist` during upload; every other archive file, including the complete app bundle, remained byte-identical. The manifest retains the pre-upload tree hash as well.

`codesign --verify --deep --strict --verbose=2` passed outside the sandbox for both the archived app and exported IPA app. The IPA is signed with Apple Distribution for the expected team; `get-task-allow=false`, `beta-reports-active=true`, and Game Center entitlement are present. The source project has In-App Purchase capability. Local signature verification is not a device smoke test.

## Upload outcome

- Uploaded the same archive with `release/ExportOptionsUpload.plist`; no rebuild between local validation and upload.
- Xcode reported `Upload succeeded` at **2026-08-29 01:24:29 JST** and `EXPORT SUCCEEDED`.
- Logged-in App Store Connect showed **Version 1.1, Build (7): Complete** in Build Uploads and **Ready to Submit** in the version list.
- Build ID: `c544f5af-c1c4-41c6-950f-3d9caa35d64e`. Existing `PIYOKEY Internal QA` and `Tester` groups are shown on build 7; no new tester/group was added by this run. Actual TestFlight installation and linking to the 1.1 distribution record are not yet confirmed.
- No App Review submission, public release, metadata Save, media replacement, or legal agreement acceptance was performed.

## Remaining submission gates

- Free Apps Agreement is Active; Paid Apps Agreement is **New**, with a legal-entity update required. Account Holder must complete applicable agreement, tax, banking, and regulatory information.
- IAP review screenshot / ready-to-submit / association with the 1.1 review, exact-build Sandbox purchase and restore scenarios remain open.
- Full RC unit/UI regression and physical-device checks are not replaced by #73's scoped regression. Run `release/TESTFLIGHT_1_1_SMOKE.md` on this exact build.
- Native-language review and final RC iPhone/iPad media remain open. Live Japanese description still lists retired Korean UI and must be corrected before review. Existing Japanese media is eight older screenshots and one preview.
- Content-rights sign-off, analytics/privacy console and live-policy checks remain open. Regular source preflight and strict release preflight have different meanings.
- Console currently has automatic release and no phased release selected. This observation is not a new release-mode approval; the explicit product-owner decision stays pending. Live copyright is `2026 Kiish`; do not overwrite it with the differing historical local value without confirmation.

PR #74 was merged with local verification and user authorization, including the user's explicit CI/separate-review exception. Actions and branch-protection settings were not changed. That exception does not waive these App Store gates.

## Repository checks for release records

Regular `python3.12 tools/release_preflight.py` passes. Strict mode still fails with 39 findings after recording the verified archive; these include shared analytics gates for other platforms and the unresolved 1.1 manual gates above. No gate was cleared merely because the build uploaded.
