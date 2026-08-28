# 언어 전체 검수 로컬 검증 — 2026-08-28

검수 대상은 `dcbc5dc` 이후 로컬 후보의 언어 관련 수정이다. 결과·남은 개선은 `docs/LANGUAGE_REVIEW.md`, 반복 절차는 `docs/LANGUAGE_EXPANSION_CHECKLIST.md`를 따른다. GitHub·스토어·원격 카탈로그는 변경하지 않았다.

| 검사 | 결과 | 로컬 로그 |
|---|---|---|
| Python 회귀 / preflight | 96 passed / pass | `python.log`, `preflight.log` |
| Swift 공용 코어 XCTest | 45 passed | `HangulEngineTests.log`, `DeckKitTests.log` |
| Kotlin 공용/설정·발견 JUnit | 63 passed | `kotlin-tests.log` |
| 실제 Foundation plural / 소수점 | 340 / 5 passed | `plural-runtime.log` |
| iOS 변경 소스 | parse pass; 앱 typecheck 아님 | `swift-parse.log` |
| UI 리소스 | Apple strings/plist parse, Android 8개 모듈 AAPT2 compile pass | `resources.log` |
| 기존 콘텐츠 보존 | 덱 67개·catalog 2개, MP3/벡터/영어 원본 585개 파일 일치 | `preservation.json` |

Swift 코어 소스는 이번 검수에서 바뀌지 않았다. 앞선 확장과 같은 실제 컴파일된 모듈을 링크해 현재 테스트·콘텐츠로 재실행했다. 모듈 재컴파일 명령은 `artifacts/language-expansion/README.md`에 있다. Kotlin은 현재 소스와 발견 테스트까지 다시 컴파일했다.

```sh
python3 -m unittest discover -s tools/tests
python3 tools/release_preflight.py
python3 artifacts/language-review/offline_checks.py swift
python3 artifacts/language-review/offline_checks.py kotlin
python3 artifacts/language-review/preservation_resources.py
swiftc -module-cache-path /private/tmp/piyokey-es-de-fr-module-cache artifacts/language-review/resource_check.swift -o artifacts/language-review/resource-check
artifacts/language-review/resource-check
```

직접 실행기·바이너리·로그는 이 Mac의 설치 도구 경로를 사용하는 로컬 산출물이며 README 외 Git 제외다. `resource_check.swift`는 실제 앱 리소스를 Foundation으로 읽어 17개 키 × 5개 언어 × 0/1/2/5를 렌더링한다. 원어민 번역 품질을 자동 보증하는 검사는 아니다.

정상 SwiftPM/Xcode/Gradle은 이전 확장에서 sandbox/cache/CoreSimulator/socket 제한으로 차단됐다. 이번 검수에서 권한을 우회하거나 제한을 해제하지 않았으며, 전체 앱 빌드·앱 단위/UI·계측·실기기·스토어 검증 통과로 기록하지 않는다. 이전 환경 로그는 `artifacts/language-expansion/`에 보존한다.

새 iOS 설정/알림 테스트는 소스에 추가했지만 앱 suite 실행은 남아 있다. Android 백그라운드 알림 locale, 복합 수량 문구, 원어민 검수·좁은 화면/큰 글자·최종 RC 촬영과 실제 Issue/소유권/PR 등록도 인계 항목이다. 최종 소스 SHA의 범위 한정 결과는 `release/evidence/<SHA>.json`에 기록한다.
