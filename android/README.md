# PIYOKEY Android (typee / ピヨキー)

Kotlin/Jetpack Compose port of the iOS app (`ios/Hanco`). Parity target: iOS public 1.1.1
plus later `main` merges. Plan and scope: [`docs/ANDROID_PORT_PLAN.md`](../docs/ANDROID_PORT_PLAN.md).

## Build & test

```bash
cd android
export JAVA_HOME=/opt/homebrew/opt/openjdk@21   # any JDK 17+
./gradlew :core:hangul:check :core:deckkit:test :core:domain:test   # pure JVM cores
./gradlew :app:testDebugUnitTest :app:assembleDebug                  # app unit tests + APK
./gradlew :app:connectedDebugAndroidTest                             # needs a device/emulator
./gradlew :app:lintDebug
```

Toolchain: AGP 9.3.1, Gradle 9.5.0, Kotlin 2.3.21, Compose BOM 2026.08.00,
compileSdk 37, targetSdk 36, minSdk 26, JVM 17. `local.properties` (untracked) holds `sdk.dir`.

## Modules

| Module | Contents | Rule |
|---|---|---|
| `:core:hangul` | Composition engine, jamo judge, 10-key recipes/flick, OS IME judge | Pure Kotlin, no Android imports. ≥95% line coverage |
| `:core:deckkit` | Deck/catalog models, locale lookup, validation, `.typedeck` reader/writer | Pure Kotlin |
| `:core:domain` | Game/practice reducers, scoring, curriculum, retention, review, recommendations | Pure Kotlin |
| `:app` | Everything Android: data stores, platform services, Compose UI | Package-per-area (below) |

`app` packages (`app.piyokey.android.*`):

```text
Services.kt            process-wide service locator (Services.app / Services.context)
data/settings/         Prefs (SharedPreferences mirroring iOS UserDefaults keys), AppSettings
data/...               JSON file stores (installed decks, progress, review, retention, …)
platform/...           audio, TTS, reminders, billing, Play Games, analytics, share, files
ui/theme/              Palette (1:1 iOS AppPalette), PiyoType, Piyo.sp, components, AdaptiveMetrics, L
ui/nav/                Navigator, AppRoot, MainTabs, LocalTabNavigator, LocalAppNavigator
feature/<area>/        home, discover, practice, game, library (My Page), settings, onboarding
```

## Conventions (all contributors / agents)

- **Parity first.** Read the iOS Swift source for the screen you port and reproduce layout,
  copy, states, rules and edge cases. Do not add features iOS does not have.
- **Strings.** Never hard-code user-visible text. Android strings are generated from iOS by
  `python3 tools/gen_android_strings.py`; iOS key `practice.title` → `R.string.practice_title`
  (`.`/`-` → `_`). iOS `%@` becomes `%s`/`%1$s`. Plurals from `.stringsdict` are `<plurals>`
  (`pluralStringResource`). Learning content (`KoreanLearningContent.strings`) is
  `R.string.ko_<key>`. Compose: `stringResource(R.string.x)`; non-Compose: `L.string(R.string.x)`.
  If a needed key does not exist on iOS either, add it to **all five** iOS `Localizable.strings`
  files and regenerate — never hand-edit `strings_generated.xml`.
- **Language.** The UI language is chosen in-app (`AppSettings.language`), not by the device.
  `LocalizedApp` swaps `LocalContext`, so `LocalContext.current` is a *configuration context*,
  not the Activity. For an Activity (billing, permissions, sign-in) use
  `LocalActivity.current` (activity-compose) or walk `ContextWrapper.baseContext`.
- **Theme.** Use `Piyo.colors.*` (never Material defaults), `PiyoType.*()` for text styles,
  `Piyo.sp(x)` for custom sizes (follows the in-app font scale), `PiyoCard`, `PrimaryButton`,
  `SecondaryButton`, `PiyoChip`, `ScreenTitle`, `PiyoBackground`. Light/dark follow the in-app
  theme setting. Touch targets ≥ 44dp.
- **Adaptive layout.** `Piyo.metrics` (`AdaptiveMetrics`) mirrors iOS `HancoAdaptiveMetrics`
  (compact < 600dp, medium, wide ≥ 900dp). Use `Modifier.centeredContent(maxWidth)`.
- **Navigation.** A destination is a class implementing `Route` with `@Composable Content()`.
  Push inside the current tab with `LocalTabNavigator.current.push(route)`. Full-screen flows
  (sessions, games, onboarding, results, editors) go on `LocalAppNavigator.current` which
  covers the tab bar. Pop with `.pop()`. Use Material3 `ModalBottomSheet`/`AlertDialog` for
  iOS sheets/alerts. Tab switching: `LocalTabController.current.select(AppTab.X)`.
- **Preferences.** Reuse iOS key strings exactly (`BoolPref("keyboard.shows_key_guide", true)`).
  Each area declares its own prefs object in its own package.
- **Persistence.** JSON files under `filesDir/Hanco/...` with the same relative paths and schema
  versions as iOS (`Core/Persistence/RecoverableJSONFile.swift`: atomic write + `.backup` +
  quarantine of corrupt files). Use kotlinx-serialization with `@SerialName` snake_case keys
  matching iOS `CodingKeys`.
- **Sessions.** No modal, purchase, file import or settings interruptions during a lesson/game.
  Background → pause timers and stop speech (use `ProcessLifecycleOwner` / `LifecycleEventEffect`).
- **Audio.** Never request audio focus (don't stop other apps' music).
- **Tests.** Pure rules go in `:core:domain` with JVM tests. App logic that needs Android APIs:
  `app/src/test` (JVM) where possible, `app/src/androidTest` for Compose UI tests.
- **Ownership.** When several people/agents work at once, only edit files in your assigned
  packages. Shared files (`ui/theme`, `ui/nav`, `Services.kt`, build files) change only by the
  integrator.
