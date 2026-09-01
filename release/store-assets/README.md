# iOS App Store 미디어 — 10개 로케일

사용자 요청일: 2026-08-28. 대상 소스: iOS `1.1 (7)`.

현재 es/de/fr 언어 확장 로컬 후보는 **재촬영 필요** 상태다. 아래 2026-08-28 오전 산출물은 과거 제작 기록이며 새 언어 검증 결과가 아니다.

## 산출물과 경계

- 검토용 갤러리: `artifacts/store-localization/delivery/index.html`
- 언어별 `<locale>/screenshots/`: PNG 10장, 1320×2868 RGB(알파 없음).
- 언어별 `<locale>/preview.mp4`: 실제 앱 녹화 기반 26.4초, 886×1920, H.264 High 4.0, 30fps, 스테레오 AAC 48kHz. 기존 일본판처럼 내레이션·배경음악 없이 무음 AAC 트랙을 사용한다.
- 언어별 `screenshot-contact.png`, `preview-contact.png`: 검토용 축소 모음. 스토어 업로드 파일이 아니다.
- 언어별 `manifest.json`, `video-validation.txt`: 체크섬과 전체 프레임 디코딩 결과.
- 전체 `manifest.json`: 제작 시점, 소스 HEAD와 dirty diff 지문, 카피 지문, 언어별 파일 목록.
- 원본 UI: `artifacts/store-localization/capture-v1/{ja,en,ko}/`의 XCTest 결과·PNG 첨부·MP4·시간 마커.

대용량 산출물은 Git에 넣지 않는다. 현재 로컬 경로에만 있으며 외부 영구 아카이브 업로드는 미실행이다. 이전 공개판 `release/screenshots/ja-marketing` 및 프리뷰 원본은 보존한다.

이번 촬영은 사용자의 최신 작업 소스를 기준으로 한 **제작용 simulator capture**다. dirty worktree의 산출물을 최종 제출 바이너리 검증으로 간주하지 않는다. 최종 release candidate가 확정되면 화면·기능 일치 확인이 필요하다. App Store Connect 업로드, 새 로케일 필수 메타데이터, 저장 후 재조회, 심사 제출은 아직 완료하지 않았다.

2026-08-28 09:16 JST 전체 재검토: 100장과 10개 영상의 contact sheet, 60개 자막, 파일 체크섬 및 7,920프레임 전체 디코딩 재검증을 통과했다. 영어 홈 버튼의 실제 UI 말줄임은 앱 폴리싱 시 확인할 항목이며 이미지에서 임의로 고치지 않았다. 스토어 반영을 시작했으나 미디어 관리자 진입 시 Apple 로그인 만료가 확인되어 **외부 변경 없이 중단**했다. Chrome과 앱 내 브라우저 모두 재로그인이 필요하다. 자세한 증빙은 `verification-20260828.json`의 `latest_review`를 참조한다.

## 다음 촬영의 로케일 매핑

| 시장 | App Store 로케일 | 실제 UI | 현지어 마케팅 |
|---|---|---|---|
| 일본 | ja | 일본어 | 일본어 |
| 미국 | en-US | 영어 | 영어 |
| 한국 | ko | 영어 | 한국어 |
| 중국 | zh-Hans | 영어 | 중국어 간체 |
| 대만 | zh-Hant | 영어 | 중국어 번체 |
| 독일 | de-DE | 독일어 | 독일어 |
| 프랑스 | fr-FR | 프랑스어 | 프랑스어 |
| 스페인 | es-ES | 스페인어 | 스페인어 |
| 브라질 | pt-BR | 영어 | 브라질 포르투갈어 |
| 인도네시아 | id | 영어 | 인도네시아어 |

이 목록은 사용자의 ‘주요 10개 국가’ 요청에 대해 언어권을 넓게 다루도록 정한 제작 범위이며, 매출 상위 10개 국가라는 의미가 아니다. `en-GB`·`en-AU`·`en-CA`는 검증된 `en-US` 미디어를 재사용할 수 있지만 실제 로케일에 업로드 후 재조회해야 한다.

es/de/fr은 해당 실제 UI, 그 외 미지원 UI 시장은 영어 UI에 현지어 마케팅 설명을 사용한다. 사용자의 후속 요청에 따라 **사진·영상에 별도의 지원 언어 안내는 넣지 않는다**. 학습 화면을 번역한 것처럼 합성하지 않는다. Pro 화면은 생성·편집·무제한 보관에 구매가 필요하다고 고지하며 가격은 하드코딩하지 않는다. 일본어 브랜드는 `ピヨキー`, 그 외 앱 브랜드는 `typee`; 상품명은 `ピヨキー プロ`·`피요키 프로`·`typee pro`다.

## 재현 절차

Python 3.11 이상과 Pillow, Xcode가 필요하다. 현재 Mac의 번들 Python은 `/Users/jungminoh/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3`이다.

1. 저장소 소유권·상태를 점검하고 iOS simulator용 `build-for-testing`을 실행한다. `artifacts/store-localization/DerivedData`를 사용하며 iPhone 17 Pro Max, iOS 26.5, 병렬 테스트 OFF로 고정한다. 다른 작업의 미커밋 변경을 reset하지 않는다.
2. `xcrun simctl status_bar <device-id> override --time '9:41' --dataNetwork wifi --wifiMode active --wifiBars 3 --batteryState charged --batteryLevel 100`으로 촬영용 상태 표시줄을 정돈한다.
3. `python3 tools/capture_global_store_assets.py --languages ja en es de fr --output artifacts/store-localization/capture-language-expansion`을 실행한다. 원본 덮어쓰기를 막으므로 재촬영은 새 output 디렉터리를 사용한다. 테스트는 `testAppStoreScreenshotGlobalJA/EN/ES/DE/FR`이며 각 언어에서 핵심 화면 8장, 10키와 Pro 편집기를 촬영한다. Pro entitlement는 DEBUG 테스트 설정이며 실제 결제 성공 증거가 아니다. 출석 상태도 일관된 가상 학습 이력으로 시드한다.
   영상·스크린샷 모드 모두 `--derived-data <build-for-testing 경로>`를 동일하게 사용하며,
   그 경로에 `.xctestrun`이 정확히 하나 없으면 캡처를 시작하지 않는다.
4. 다음 Swift 도구를 각각 `swiftc`로 컴파일한다.

```sh
swiftc tools/generate_app_store_marketing_screenshots.swift -o /tmp/typee-screenshot-renderer
swiftc tools/compose_app_preview.swift -o /tmp/typee-preview-composer
swiftc tools/app_preview_video.swift -o /tmp/typee-preview-checker
swiftc tools/add_silent_app_preview_audio.swift -o /tmp/typee-preview-audio
```

5. `python3 tools/build_global_store_assets.py --capture artifacts/store-localization/capture-language-expansion --output artifacts/store-localization/delivery-language-expansion --videos`를 실행한다. 번역은 `localizations.json`을 단일 기준으로 사용한다. 글자 높이 초과 시 폰트를 제한된 범위에서 줄이고, 그래도 넘치면 실패한다. 영상 시작·종료는 XCTest 마커를 기준으로 잡되 녹화 후 실제 프레임을 반드시 확인한다. `--locales ja en-US`로 일부만 재생성할 수 있다.
6. 모든 언어의 이미지 contact sheet와 영상 contact sheet, 대표 원본 PNG·영상 시작/종료 프레임을 확인한다. 영상 검증은 15–30초, 해상도·30fps·H.264·스테레오 AAC·전체 디코딩·프레임 timestamp 단조 증가를 확인한다. 인코더는 영상 12Mbps와 오디오 256kbps를 요청하지만 단색 UI/무음 콘텐츠의 실측 비트레이트는 낮을 수 있어 최종 Apple 처리 성공도 별도 확인한다.
7. `python3 -m unittest tools.tests.test_store_asset_copy tools.tests.test_release_preflight`와 `python3 tools/release_preflight.py`를 실행한다. 이 통과는 저장소 검사이며 스토어·기기·권리 gate 통과를 뜻하지 않는다.

## 업로드 체크

- [ ] 신규 7개 로케일의 이름·설명·지원 URL 등 필수 메타데이터를 준비·검수한다.
- [ ] 현지어 최종 사람 검수(아직 미실행). 자동/AI 문구 검수와 별도로 기록한다.
- [ ] 최종 제출용 바이너리와 각 UI 촬영 내용의 일치를 확인한다.
- [ ] 사용자가 확인한 미디어를 App Store Connect의 1.1 iPhone 6.9인치 슬롯에 업로드한다.
- [ ] 언어별 이미지 10장·영상 1개 순서와 Apple의 영상 처리 성공을 다시 읽어 확인한다.
- [ ] 영어 변형 3개 로케일에 영어 미디어를 적용하고 primary 전환 차단이 해소되는지 확인한다. 모든 버전의 영어 필수 자산 조건은 별도다.
- [ ] 앱 개인정보·라이브 웹 문구·IAP 심사 스크린샷·Paid Apps Agreement 등 기존 gate를 유지한다. 이번 미디어 생성만으로 앱 심사를 제출하지 않는다.

규격 참고: [Apple App Preview specifications](https://developer.apple.com/help/app-store-connect/reference/app-information/app-preview-specifications/), [Apple Screenshot specifications](https://developer.apple.com/help/app-store-connect/reference/app-information/screenshot-specifications/). 2026-08-28 확인.
