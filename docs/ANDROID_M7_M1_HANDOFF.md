# Android M7 M1 selective-transfer handoff

기준일: 2026-08-22 JST

이 문서는 현재 대규모 dirty worktree를 정리하거나 commit하지 않고 Android A0와
M1 완료 단위만 중앙 `piyokey` 저장소의 전용 브랜치로 선별 이전하기 위한 정확한
파일 경계를 기록한다. M2 기능은 포함하지 않는다.

## 1. Android 소유 파일 — 39개

아래 `android/` 파일은 모두 이번 Android A0+M1 단위가 소유한다.

```text
android/README.md
android/app/build.gradle.kts
android/app/src/main/AndroidManifest.xml
android/app/src/main/java/app/piyokey/piyokey/MainActivity.kt
android/app/src/main/res/values-ja/strings.xml
android/app/src/main/res/values-ko/strings.xml
android/app/src/main/res/values/strings.xml
android/app/src/main/res/values/styles.xml
android/app/src/main/res/xml/backup_rules.xml
android/app/src/main/res/xml/data_extraction_rules.xml
android/build.gradle.kts
android/core/deckkit/build.gradle.kts
android/core/deckkit/src/main/kotlin/app/piyokey/core/deckkit/DeckKitJson.kt
android/core/deckkit/src/main/kotlin/app/piyokey/core/deckkit/JsonSchemaValidator.kt
android/core/deckkit/src/main/kotlin/app/piyokey/core/deckkit/Models.kt
android/core/deckkit/src/main/kotlin/app/piyokey/core/deckkit/Validation.kt
android/core/deckkit/src/test/kotlin/app/piyokey/core/deckkit/DeckKitContractTest.kt
android/core/hangul/build.gradle.kts
android/core/hangul/src/main/kotlin/app/piyokey/core/hangul/HangulComposer.kt
android/core/hangul/src/main/kotlin/app/piyokey/core/hangul/HangulTables.kt
android/core/hangul/src/main/kotlin/app/piyokey/core/hangul/JamoSequence.kt
android/core/hangul/src/main/kotlin/app/piyokey/core/hangul/OSIMETextJudge.kt
android/core/hangul/src/test/kotlin/app/piyokey/core/hangul/HangulEngineTest.kt
android/core/hangul/src/test/kotlin/app/piyokey/core/hangul/OSIMETextJudgeTest.kt
android/core/hangul/src/test/kotlin/app/piyokey/core/hangul/SharedTestVectorsTest.kt
android/core/piyodeck/build.gradle.kts
android/core/piyodeck/src/main/kotlin/app/piyokey/core/piyodeck/PiyoDeckModels.kt
android/core/piyodeck/src/main/kotlin/app/piyokey/core/piyodeck/PiyoDeckPackageCodec.kt
android/core/piyodeck/src/main/kotlin/app/piyokey/core/piyodeck/PiyoDeckStrictJson.kt
android/core/piyodeck/src/main/kotlin/app/piyokey/core/piyodeck/PiyoDeckUserDeckValidator.kt
android/core/piyodeck/src/main/kotlin/app/piyokey/core/piyodeck/PiyoDeckZip.kt
android/core/piyodeck/src/test/kotlin/app/piyokey/core/piyodeck/PiyoDeckPackageTest.kt
android/gradle.properties
android/gradle/libs.versions.toml
android/gradle/wrapper/gradle-wrapper.jar
android/gradle/wrapper/gradle-wrapper.properties
android/gradlew
android/gradlew.bat
android/settings.gradle.kts
```

## 2. 이번 Android 작업이 변경한 공용·iOS·CI 파일 — 49개

```text
.github/workflows/source-ci.yml
AGENTS.md
DECISIONS.md
PRD.md
README.md
docs/ANDROID_M7_CONTRACTS.sha256
docs/ANDROID_M7_M1_HANDOFF.md
docs/ANDROID_M7_READINESS.md
ios/HangulEngine/Sources/DeckKit/JSONSchemaValidator.swift
ios/HangulEngine/Sources/DeckKit/Models.swift
ios/HangulEngine/Sources/DeckKit/PiyoDeckPackageReader.swift
ios/HangulEngine/Sources/DeckKit/PiyoDeckPackageWriter.swift
ios/HangulEngine/Sources/DeckKit/PiyoDeckZIP.swift
ios/HangulEngine/Sources/DeckKit/UserDeckValidator.swift
ios/HangulEngine/Tests/DeckKitTests/DeckKitTests.swift
ios/HangulEngine/Tests/DeckKitTests/PiyoDeckPackageTests.swift
ios/HangulEngine/Tests/HangulEngineTests/HangulEngineTests.swift
ios/Hanco/Hanco/App/HancoApp.swift
shared/mock_catalog/catalog.json
shared/mock_catalog/updates/catalog.json
shared/mock_catalog/updates/decks/official_daily_words_v5.json
shared/piyodeck/SPEC.md
shared/piyodeck/fixtures/cases.json
shared/piyodeck/fixtures/invalid/README.md
shared/piyodeck/fixtures/invalid/bom-deck.piyodeck
shared/piyodeck/fixtures/invalid/crc-mismatch.piyodeck
shared/piyodeck/fixtures/invalid/data-descriptor.piyodeck
shared/piyodeck/fixtures/invalid/deflate-method.piyodeck
shared/piyodeck/fixtures/invalid/duplicate-key.piyodeck
shared/piyodeck/fixtures/invalid/encrypted.piyodeck
shared/piyodeck/fixtures/invalid/future-version.piyodeck
shared/piyodeck/fixtures/invalid/header-mismatch.piyodeck
shared/piyodeck/fixtures/invalid/hidden-preamble.piyodeck
shared/piyodeck/fixtures/invalid/unpaired-surrogate.piyodeck
shared/piyodeck/fixtures/invalid/unsafe-path.piyodeck
shared/piyodeck/fixtures/invalid/wrong-sha.piyodeck
shared/piyodeck/fixtures/invalid/zip64.piyodeck
shared/piyodeck/fixtures/valid/README.md
shared/piyodeck/fixtures/valid/basic.piyodeck
shared/piyodeck/fixtures/valid/pretty-basic.piyodeck
shared/schema/catalog.schema.json
shared/schema/deck.schema.json
shared/test_vectors.json
tools/gen_mock_catalog.py
tools/gen_piyodeck_fixtures.py
tools/gen_test_vectors.py
tools/piyodeck_tool.py
tools/tests/test_mock_catalog_snapshots.py
tools/tests/test_piyodeck_tool.py
```

## 3. 선별 이전 대상 저장소에 반드시 이미 있어야 하는 iOS 1.1 입력

아래 파일은 이번 Android 작업이 새로 소유한 변경이 아니지만, M1 공용 테스트가
직접 의존한다. 중앙 기준선에 없다면 Android 변경과 별도로 iOS 1.1 기준선에서
함께 가져와야 한다.

```text
shared/schema/piyodeck-manifest-v1.schema.json
shared/piyodeck/fixtures/valid/basic-deck.json
shared/piyodeck/fixtures/valid/basic-manifest.json
shared/piyodeck/fixtures/invalid/audio-deck.json
shared/piyodeck/fixtures/invalid/official-deck.json
shared/piyodeck/fixtures/invalid/unknown-field-deck.json
ios/HangulEngine/Sources/DeckKit/PiyoDeckImportError.swift
ios/HangulEngine/Sources/DeckKit/PiyoDeckManifest.swift
ios/HangulEngine/Sources/DeckKit/PiyoDeckStrictJSON.swift
```

## 4. 검증 기준

```sh
python3 tools/gen_piyodeck_fixtures.py
python3 -m unittest discover -s tools/tests -p 'test_*.py'
python3 tools/release_preflight.py
(cd ios/HangulEngine && swift test --disable-sandbox)
(cd android && ./gradlew :core:hangul:jacocoTestReport :core:hangul:jacocoTestCoverageVerification :core:deckkit:test :core:piyodeck:test :app:lintDebug :app:assembleDebug --no-daemon)
shasum -a 256 -c docs/ANDROID_M7_CONTRACTS.sha256
```

완료 실측은 Kotlin 34/34, SwiftPM 42/42, Python 49/49, Hangul line
99.68%·branch 95.78%, release preflight·lintDebug·assembleDebug 통과다.

공유 `cases.json`은 valid 2개·malicious 13개를 기록한다. Python·Swift는 15개
binary를 data-driven으로 직접 실행하고, Kotlin은 공용 SHA·Unicode binary 2개와
나머지 동일 공격군의 deterministic in-memory mutation 회귀를 실행한다. 이는 이번
M1 인계의 확정 범위이며 별도 테스트 확대는 하지 않는다.

## 5. 의도적으로 하지 않은 작업

- Git commit, tag, stage, stash, clean, branch 생성
- Git remote 추가 또는 GitHub 외부 상태 변경
- M2 키보드·연습 UI, 저장, 오디오, Billing, Play Games 기능 착수
