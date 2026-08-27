# Android release distribution handoff

## What is ready in source

- The Android 1.1 baseline is `versionName 1.1.0`, `versionCode 8`, minSdk 26, targetSdk 36.
- ordinary local and Source CI Release builds stay unsigned and can produce both the minified APK and AAB;
- R8 and resource shrinking are enabled for Release;
- the merged Release manifest is checked for `debuggable != true`, `allowBackup=false`, and no cleartext opt-in;
- runtime catalog, privacy, support, Play Games, and upload signing values come only from same-named private Gradle properties or environment values;
- `bundleDistributionRelease` fails before the AAB build unless every external release gate is present.

The source contract does not confirm ownership of `app.piyokey.piyokey`, create a Play app, generate an upload identity, accept content rights, or mutate Play Console.

## Ordinary CI build

```sh
cd android
./gradlew \
  :app:verifyReleaseManifestContract \
  :app:assembleRelease \
  :app:bundleRelease \
  --no-daemon
```

Expected unsigned outputs:

- `app/build/outputs/apk/release/app-release-unsigned.apk`
- `app/build/outputs/bundle/release/app-release.aab`

These are build evidence only and must not be uploaded.

## Private distribution inputs

Use `android/release/distribution.properties.example` as the complete key list. Put real values in private `~/.gradle/gradle.properties` or inject same-named environment values. Do not copy the completed file into the repository and do not print the environment in logs.

The final application ID confirmation must equal the configured application ID. Version name must be three numeric components and version code must be 8 or newer. The catalog, privacy, and support endpoints must be public HTTPS URLs, not localhost or example hosts. Play Games requires one numeric project ID plus all 20 Console-issued `Cgk…` resource IDs. The upload keystore path must exist. `PIYOKEY_CONTENT_RIGHTS_CONFIRMED=true` is a human approval gate, not a value to set merely to make the task green.

## Distribution build

```sh
cd android
./gradlew :app:bundleDistributionRelease --no-daemon --no-configuration-cache
```

The task runs the merged-manifest contract and fail-closed distribution preflight before `bundleRelease`. `--no-configuration-cache` is mandatory whenever all signing inputs are present so Gradle cannot serialize signing secrets. Missing or invalid inputs are reported by key name only; passwords, aliases, resource IDs, and paths are never echoed by the task. A wrong keystore password or alias then fails Android's signing task.

Before upload, record SHA-256 and verify the AAB signature locally:

```sh
shasum -a 256 app/build/outputs/bundle/release/app-release.aab
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
```

## External and final-device gates

Issue #19 remains the single release-candidate gate. Only after the user confirms the package and upload identity should Play Console app creation, product setup, 15 leaderboards, 5 achievements, internal testing, and real Billing/Games checks occur. The same final candidate then receives the one-time physical-device pass for input latency/rollover, 60-second game frames, OS IME, audio mixing/TTS, SAF import/export, reminders, and purchase/restore. A green source preflight is not permission to upload or release.
