# es/de/fr 로컬 검증 — 2026-08-28

작업 기준 main: `47371de950dcb28530f69b91ec596f53e2818ab3`. 독립 clone/branch `outputs/local-language-expansion` / `codex/local-es-de-fr`. 원본 dirty 파일, 원격 GitHub/스토어/카탈로그는 변경하지 않았다.

## 확인한 범위

| 검사 | 결과 | 로컬 로그 |
|---|---|---|
| Python 도구 회귀 | 92 passed | `python.log` |
| repository preflight | pass | `preflight.log` |
| Swift 공용 코어 XCTest | HangulEngine 11 + DeckKit 18 + package 16 = 45 passed | `HangulEngineTests.log`, `DeckKitTests.log` |
| Kotlin 공용/설정 JUnit | 57 passed | `kotlin-tests.log` |
| Swift 앱 변경 소스 | parse만 pass, typecheck/UI 실행 아님 | `swift-parse.log` |
| UI 리소스 | Apple strings 파싱·중복 키 검사, Android 8개 모듈 AAPT2 compile pass | `resources.log` |
| 원본 보존 감사 | deck snapshots 67, catalog 2, MP3·벡터·영어 번역 원본 585개 파일 일치 | `preservation.json` |

`localized-deck.json` / `localized.typedeck` 공용 golden으로 Python·Swift·Kotlin writer의 바이트 일치와 reader 왕복을 확인했다. `tools/gen_piyodeck_fixtures.py`로 재생성한다. 기존 악성/정상 package goldens는 그대로 유지한다.

보존 감사는 새 es/de/fr 필드, version·updated/generated_at·file_url·size_bytes만 제외한 기존 전체 JSON 의미를 비교한다. 한국어 목표·ID·원문·ja/en/ko 값·순서, MP3 bytes와 공용 조합 벡터가 보존됐다. 신규 언어의 현지어 사람 검수는 수행하지 않았다.

## 제한과 재실행

정상 SwiftPM은 `sandbox-exec: sandbox_apply: Operation not permitted`에서 차단됐다. Xcode는 전역 module/manifest cache와 CoreSimulator 접근에서 차단됐다. Gradle은 FileLockContentionHandler의 로컬 socket 생성에서 차단됐다. sandbox/보호 설정을 해제하거나 권한을 우회하지 않았다. 관련 로그는 `swiftpm.log`, `xcode-project.log`, `android.log`다. 따라서 **앱 전체 빌드·앱 단위/UI·계측·기기/스토어 검증 통과 증거가 아니다.**

공용 코어는 설치된 Swift compiler/XCTest, Kotlin 2.3.21 compiler/JUnit 4.13.2로 실제 기존 테스트 소스를 직접 컴파일·실행했다. 이 Mac에서 사용한 환경 한정 runner `offline_checks.py`, 보존/리소스 감사 `audit.py`와 바이너리·로그는 이 디렉터리에 로컬로 보존한다(README 외 Git 제외). 원격 handoff 때는 정상 개발 환경의 표준 명령을 우선한다.

```sh
# 이 독립 clone 루트에서 실행 (로컬 runner는 이 Mac의 절대 경로 사용)
/opt/homebrew/bin/python3.12 -m unittest discover -s tools/tests
/opt/homebrew/bin/python3.12 tools/release_preflight.py
/opt/homebrew/bin/python3.12 artifacts/language-expansion/offline_checks.py swift
/opt/homebrew/bin/python3.12 artifacts/language-expansion/offline_checks.py kotlin
/opt/homebrew/bin/python3.12 artifacts/language-expansion/audit.py
```

Swift runner의 실제 모듈 빌드 선행 단계:

```sh
swiftc -module-cache-path /private/tmp/piyokey-es-de-fr-module-cache -emit-module -emit-library -enable-testing -module-name HangulEngine ios/HangulEngine/Sources/HangulEngine/*.swift -emit-module-path artifacts/language-expansion/native/HangulEngine.swiftmodule -o artifacts/language-expansion/native/libHangulEngine.dylib
swiftc -module-cache-path /private/tmp/piyokey-es-de-fr-module-cache -emit-module -emit-library -enable-testing -module-name DeckKit -I artifacts/language-expansion/native -L artifacts/language-expansion/native -lHangulEngine ios/HangulEngine/Sources/DeckKit/*.swift -emit-module-path artifacts/language-expansion/native/DeckKit.swiftmodule -o artifacts/language-expansion/native/libDeckKit.dylib
```

정상 전체 앱 검증/새 RC 촬영/현지어 검수/원격 Issue·Project 등록·소유권/CI·PR gate는 `docs/LANGUAGE_EXPANSION_CHECKLIST.md`에 남긴다. `workspace-doctor.log`는 소유권 미등록을 명시한다. 사용자 로컬 우선 요청에 따라 실제 Issue 없이 가짜 claim을 만들지 않았으며 CI/리뷰 예외를 새로 적용하지 않았다.
