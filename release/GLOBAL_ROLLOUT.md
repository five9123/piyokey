# typee / ピヨキー 글로벌 출시 계획

- 상태: App Store Connect 글로벌 등록 준비 완료 — 출시·규제 gate 미완료
- 작성일: 2026-08-14
- 대상: 한국을 포함한 전 세계 App Store
- 기준 앱: App Store Connect 앱 ID `6794853985`, Bundle ID `app.piyokey.Piyokey`
- 현재 제출 기록: `release/app_store_submission.json`의 `1.0.2 (6)` 일본 단독 제출과 그 안의 취소된 build 5 이력은 변경하지 않는다.

## 1. 단일 앱과 브랜드 규칙

기존 앱 레코드 하나를 글로벌로 확장한다. 동일 기능을 국가별 별도 Bundle ID나 중복 앱으로 제출하지 않는다. 기존 사용자의 학습 기록, 리뷰, 제품 페이지와 `piyokey.*` Game Center ID를 그대로 이어 간다.

| 해석된 앱 언어 | 홈 화면·앱 내부·공유 카드 브랜드 | App Store 로케일 이름 |
|---|---|---|
| 일본어 (`ja`) | `ピヨキー` | `韓国語タイピング - ピヨキー` |
| 한국어 (`ko`) | `typee` | `한글 타자 연습 - typee` |
| 영어 (`en`) | `typee` | `Korean Typing - typee` |
| 스페인어 (`es`) | `typee` | 스페인어 스토어 메타데이터는 별도 검증 |
| 그 밖의 언어 | 영어로 fallback → `typee` | 영어 fallback → `Korean Typing - typee` |

브랜드는 국가가 아니라 앱 언어를 따른다. 일본어에서는 `ピヨキー`, 한국어·영어·미지원 언어에서는 `typee`를 표시한다. 따라서 일본 밖의 일본어 사용자에게 `ピヨキー`가, 일본 스토어의 비일본어 사용자에게 `typee`가 보일 수 있다.

App Store 제품 페이지의 이름·부제·설명은 등록된 스토어 로케일에 따라 표시되고, 설치 뒤 홈 화면 이름은 로컬라이즈한 `CFBundleDisplayName`과 기기/앱 언어를 따른다. Custom Product Page는 앱 이름이나 부제를 바꾸는 수단으로 사용하지 않는다.

## 2. 제품·콘텐츠 현지화 계약

- 앱 1.1의 UI 로케일은 `ja`·`en`·`es`를 제공한다. 기존 한국어 UI 설정은 영어로 전환하며 학습 원본은 수정하지 않는다.
- `ja`: 기존 일본어 UI, 일본어 뜻, 가타카나 읽기를 유지한다.
- `en`: 전체 UI를 자연스러운 영어로 제공하고, 공식 덱은 영어 뜻과 라틴 문자 로마자를 갖춘다.
- `es`: 전체 UI는 스페인어이며 학습 뜻·읽기·덱 메타데이터·덱 편집 언어는 기존 영어를 사용한다.
- `ko`: 앱 UI로 제공하지 않는다. 기존 한국어 목표·번역·읽기·발음·덱 메타데이터는 보존하고 영어 UI에서는 기존 영어 콘텐츠를 표시한다.
- `en`·`es`·`ko`·미지원 언어에서는 일본어 뜻이나 가타카나를 fallback으로 노출하지 않는다.
- 첫 실행은 기기 선호 언어 중 `ja`·`en`·`es` 첫 일치를 사용하고, 일치하지 않으면 영어로 시작한다. 사용자가 설정에서 고른 앱 언어는 영속화한다.
- Android M7은 2026-08-21 사용자 결정으로 재개했으며 Google Play는 iOS App Store와 분리된 출시 트랙으로 준비한다. Android 소스·listing 준비가 iOS 1.1 제출 범위를 변경하지 않는다.

## 3. App Store 메타데이터

기계 판독 가능한 초안은 `release/global_app_store_metadata.json`을 기준으로 한다.
`release/app_store_metadata.json`의 일본어 primary와 `initial_availability: JPN`은 기존 일본 출시 카피·IAP 참조용 레거시 값이며, 1.1 글로벌 availability의 소스가 아니다.

- 기존 메타데이터 저장 로케일은 `en-US`(planned primary), `en-GB`, `en-AU`, `en-CA`, `ko`, `ja` 여섯 개다. 2026-08-28 요청에 따른 미디어 제작 대상은 일본·미국·한국·중국·대만·독일·프랑스·스페인·브라질·인도네시아의 10개 로케일이다. 영어 변형 로케일은 같은 영어 미디어를 사용할 수 있다.
- `global_app_store_metadata.json`의 `copy_from`은 초안 중복을 줄이기 위한 표기일 뿐 App Store Connect 값이 아니다. 제출 전 en-GB·en-AU·en-CA의 필수 필드와 스크린샷을 실제 로케일에 저장하고 다시 읽어 확인한다.
- 모든 App Store 표시명은 `현지화된 기능 설명 - 브랜드` 순서로 작성하고 브랜드만 앞에 단독 배치하지 않는다.
- 일본어 로케일: 이름 `韓国語タイピング - ピヨキー`; 현재 일본어 설명과 스크린샷의 제품 약속을 유지한다.
- 한국어 스토어 로케일: 이름 `한글 타자 연습 - typee`; 지원 UI가 일본어·영어·스페인어임을 설명하고 영어 UI로 캡처한 스크린샷을 사용한다. 학습 콘텐츠는 변경하지 않는다.
- 영어 로케일: en-US·en-GB는 `Korean Typing - typee`, 이름 충돌이 확인된 en-AU·en-CA는 저장 가능한 `Korean Typing Practice - typee`를 사용하고 영어 뜻·로마자 읽기를 명시한다.
- 영어·한국어 설명은 일본어 뜻이나 가타카나가 제공된다고 주장하지 않는다.
- 2026-08-28 en-US primary 변경은 Apple이 각 버전의 필수 영어 스크린샷을 먼저 요구해 저장되지 않았다. 영어 스크린샷을 등록하기 전까지 primary는 일본어로 유지한다.
- 앱 이름 변경 전에 지원·마케팅·개인정보처리방침 페이지의 `typee`/`ピヨキー` 표기와 연락처를 함께 검수한다.
- 사용자 요청으로 `zh-Hans`·`zh-Hant`·`de-DE`·`fr-FR`·`es-ES`·`pt-BR`·`id`의 미디어 현지화를 허용한다. 원본은 실제 영어 UI이며 현지어 마케팅 설명을 얹는다. 사진·영상의 별도 지원 언어 안내는 사용자의 후속 요청으로 제거하고, 실제 UI와 Pro 구매 안내는 유지한다. 카피는 `release/store-assets/localizations.json`, 제작·검증 절차는 `release/store-assets/README.md`를 따른다. 새 로케일의 필수 메타데이터 저장과 스토어 업로드는 아직 별도 gate이며, 번역 이미지 생성만으로 해당 로케일 등록을 완료 처리하지 않는다.
- 후속 #65/#67에서 앱 UI가 ja/en/es로 변경됐다. 기존 미디어는 제작 이력으로 보존하며 한국어·스페인어 스토어의 최종 후보 화면을 각각 영어·스페인어 UI로 재촬영한다.

## 4. Availability 계약

앱 1.1은 App Store Connect에서 `All Countries or Regions`를 선택하고 향후 추가되는 storefront도 자동 포함한다. 국가별 wave나 수동 allowlist는 운영하지 않는다. Deck Maker IAP도 앱과 같은 전체 storefront availability를 선택한다.

이 선택은 모든 지역에서 실제 판매 가능 상태가 자동 보장된다는 뜻은 아니다. Apple 또는 현지 규제가 추가 정보·라이선스를 요구하면 해당 storefront가 `Action Required` 또는 판매 불가로 남을 수 있다. 이 경우에도 전체 국가 선택은 유지하고 App Store Connect의 국가별 상태에서 규제 예외와 필요한 조치를 추적한다.

## 5. 공통 출시 게이트

- `ja`·`en` UI 문자열 누락 및 브랜드 문자열 회귀 테스트 통과
- 앱과 Deck Maker IAP의 availability가 `All Countries or Regions`이며 향후 storefront 자동 포함이 켜져 있음
- `es`·`fr`·`zh-Hant`·`ar` 등 미지원 기기 언어의 새 설치가 영어 UI와 `typee`로 안전하게 시작함
- 공식 덱의 영어 뜻·로마자와 영어/한국어 덱 메타데이터 스키마·콘텐츠 검증 통과
- 일본어가 아닌 앱 언어에서 일본어 뜻·가타카나 fallback이 발생하지 않음
- App Store 이름이 ja=`韓国語タイピング - ピヨキー`, ko=`한글 타자 연습 - typee`, en-US·en-GB=`Korean Typing - typee`, en-AU·en-CA=`Korean Typing Practice - typee`이며 홈 화면·인앱 브랜드는 ja=`ピヨキー`, 그 밖의 언어=`typee`인지 확인
- 영어·한국어 App Store 설명, 키워드, 스크린샷, 지원·개인정보처리방침 페이지 검수
- 기존 설치 데이터, 덱, 리뷰, Game Center 기록과 ID가 유지됨
- 현재 심사 중 제출과 충돌하지 않는 새 앱 버전/빌드로 제출

## 6. 지역별 실제 판매 상태 게이트

### EU

- App Store Connect에서 Digital Services Act에 따른 trader/non-trader 상태를 명시한다.
- trader로 배포할 경우 Apple에 제공·표시되는 주소, 전화번호, 이메일을 검증하고 공개 범위를 승인한다.
- EU용 개인정보처리방침·지원 정보·콘텐츠 권리·소비자 문의 경로가 실제 앱 동작과 일치하는지 확인한다.
- `All Countries or Regions` 선택과 별개로, 위 항목을 완료해 EU storefront가 실제 판매 가능 상태인지 확인한다.

### 중국 본토

- 앱의 교육/게임 분류, Game Center 기능, 사전 생성 음원, 정적 카탈로그가 현지 허가·신고 대상인지 전문 검토한다.
- 필요한 경우 유효한 앱 filing/라이선스 정보를 준비하고 App Store Connect 요구 필드에 등록한다.
- 중국 본토에서 접근 가능한 개인정보처리방침·지원 URL과 정적 콘텐츠 호스트를 실기기 네트워크에서 확인한다.
- 전체 국가 선택 후 중국 본토가 `Missing ICP Filing`·`Missing Game Registration` 등으로 판매 불가라면 그 상태와 필요한 조치를 기록한다. 승인·등록 없이 판매 가능하다고 간주하지 않는다.

### 베트남

- 앱의 Education 기본 카테고리와 Games/Word 보조 카테고리, 실제 6개 게임이 베트남 게임 라이선스 대상인지 확인한다.
- 라이선스가 필요하면 App Store Connect의 베트남 상태와 제출 문서를 기록하고, 충족되기 전에는 전체 국가 선택과 별개로 베트남이 실제 판매 가능하다고 간주하지 않는다.

## 7. Apple 정책 근거

- [App information localization](https://developer.apple.com/help/app-store-connect/manage-app-information/localize-app-information/)
- [App Store localizations reference](https://developer.apple.com/help/app-store-connect/reference/app-information/app-store-localizations/)
- [Managing the app information property list](https://developer.apple.com/documentation/bundleresources/managing-your-app-s-information-property-list)
- [`CFBundleDisplayName`](https://developer.apple.com/documentation/bundleresources/information-property-list/cfbundledisplayname)
- [Custom product page versions](https://developer.apple.com/help/app-store-connect/create-custom-product-pages/configure-multiple-product-page-versions)
- [App Review Guidelines 4.3 — Spam](https://developer.apple.com/app-store/review/guidelines/#spam)
- [Manage availability for your app](https://developer.apple.com/help/app-store-connect/manage-your-apps-availability/manage-availability-for-your-app-on-the-app-store)
- [Regional app information requirements](https://developer.apple.com/help/app-store-connect/reference/app-information/app-information/)

2026-08-28 App Store Connect에서 앱과 IAP의 175개 국가 또는 지역 및 향후 신규 storefront 자동 포함을 저장했다. 당시 일본 1개 지역은 Available, 145개 지역은 Processing to Available, EU 29개 지역은 Trader Status Not Provided였다. ja·en-US·en-GB·en-AU·en-CA·ko 이름과 1.1 제품 페이지 메타데이터, ja·en-US·ko IAP 현지화를 저장했다. 빌드·최신 스크린샷·IAP 심사 스크린샷·Paid Apps Agreement·DSA 등 출시 gate는 별도로 남아 있다.

Android는 별도 Google Play 출시 트랙에서 진행 중이다. 이 문서의 실행 범위에는 Play Console 변경이나 Android 배포 승인이 포함되지 않으며, Android listing 계약은 `release/google_play_metadata.json`과 `release/GOOGLE_PLAY_QA.md`에서 관리한다.
