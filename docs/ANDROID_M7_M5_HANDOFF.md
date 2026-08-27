# Android M7 M5 handoff

## Scope

Issue #24 implements PRD F4, F5.6, F7, F8, F12 and §9 on top of merged M4. It does not run the connected Galaxy or claim the deferred physical release gates in Issue #19.

## Product behavior

- The Practice tab opens a 6-chapter, 7-stage offline curriculum. Every stage has 10 fixed questions; chapters 1–4 unlock sequentially and chapters 5–6 are freely selectable.
- A stage clears at 80% accuracy. Stars match iOS thresholds: one at clear, two at 90% + 40 characters/min, three at 97% + 60 characters/min.
- Checkpoints preserve the exact target, accepted jamo prefix, mistake/resolution data and active duration. Background time is excluded and a completed checkpoint safely resumes its delayed transition.
- Lesson/deck/game mistakes enter Review. Review practice increments consecutive no-miss runs and graduates at three; installed-deck words can also be added manually and active items can be removed.
- The session-start JST day is retained across midnight. Curriculum, game or daily completion stamps that day. The home card always shows the current Monday–Sunday week and distinct completed/missed/today/upcoming states.
- MY Piyo uses the same deterministic localized encouragement on Home and Profile. Profile exposes current/longest streak, total stamps and permanent 3/5/7 weekly reward state.
- Daily Challenge selects five fixed curriculum words deterministically from the JST day and runs entirely offline.
- Daily reminder is OFF by default, requests Android 13+ notification permission only when enabled, accepts a user time, uses an inexact local alarm, survives reboot, and opens PIYOKEY when tapped. No reminder/settings modal is reachable from a running lesson/game.
- M3 home recommendations (3) and result same-tag recommendations (2) remain in their existing positions after the retention card/result metrics.

## Architecture

- `core:retention`: Android-free curriculum catalog and policies for stars/unlock, JST dates/week/streak/rewards, daily selection and review mutation.
- `core:session`: validated checkpoint restore plus a pure active-duration clock.
- `core:data`: Room v3 entities and transactions for `UserProgress`, active curriculum session, `ReviewItem`, `StreakDay`, reward and reminder state. Failed completion/checkpoint writes remain retryable from the home non-modal banner.
- `core:platform`: notification/alarm and boot adapter.
- `feature:retention`: ja/en/ko Compose views; app navigation owns session boundaries and permission launching.

## Migration

`MIGRATION_2_3` adds six tables without replacing or rewriting M3/M4 tables. Instrumentation creates a real v2 database, inserts M4 progress, migrates to v3 and validates both preservation and empty learning tables.

## Automated verification

- JVM: session 14, game 11, retention 9, data 7 tests.
- API 35 AVD `hantap_test`: data/migration 12 tests; app M3–M5 6 tests.
- Relevant app, retention and platform lint: zero errors.
- Debug app and instrumentation APKs: assembled successfully.
- The source CI Android job runs M1–M5 core contracts, lint and APK assembly on JDK 17.

## Visual evidence

The 320×640 API 35 AVD was inspected for Home, Daily Practice, Curriculum and Profile/Reminder. Cards, locked states, metrics and the five-tab bar remained within the viewport. The reproducible test screenshots are emitted under `m5-evidence`; the checked-in milestone capture and README are under `artifacts/android-m5/`.

## Deferred release gates

- Galaxy touch-down→frame-commit p95 ≤ 50 ms and 50-pair multi-pointer rollover.
- 60-second game frame performance.
- Android notification permission/delivery and external audio/IME checks on the release candidate.
- Final application ID, launcher icon/signing and Play Console configuration.

These remain Release Gate #19 and are not M5 completion evidence.
