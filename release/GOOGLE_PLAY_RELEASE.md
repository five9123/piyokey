# Google Play 출시 런북 (Android typee / ピヨキー)

상태: **소스·자동 검증 완료, 외부 gate 미완료**. 이 문서의 외부 gate가 모두 닫히기 전에는
Android 출시 완료로 표현하지 않는다. 기준 계획은 `docs/ANDROID_PORT_PLAN.md`.

## 1. 로컬 검증 (매 후보 SHA)

```bash
cd android
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew :core:hangul:check :core:deckkit:test :core:domain:test :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest            # 에뮬레이터/기기 1대, 다른 설치와 동시 실행 금지
./gradlew :app:assembleRelease :app:bundleRelease :app:verifyReleaseManifestContract
python3 tools/gen_android_strings.py --check        # 저장소 루트에서
python3 tools/gen_analytics_contract.py --check
```

2026-09-30 기준 결과: JVM 499 (hangul 81 · deckkit 50 · domain 202 · app 166), 계측 UI 54,
lint error 0, Hangul coverage line 99.85% / branch 95.99%, release APK 10.0 MB · AAB 14.3 MB.

## 2. 배포 AAB 만들기

`bundleDistributionRelease`는 아래 입력이 모두 있어야만 서명 AAB를 만든다(값은 출력하지 않음).
서명 입력이 있으면 `--no-configuration-cache`가 필수다.

| 입력 | 설명 |
|---|---|
| `PIYOKEY_APPLICATION_ID` / `PIYOKEY_APPLICATION_ID_CONFIRMED` | 기본 `app.piyokey.piyokey`. Play Console 등록 전 사용자 확정 필요 |
| `PIYOKEY_VERSION_CODE`, `PIYOKEY_VERSION_NAME` | 기본 1 / 1.1.2 |
| `PIYOKEY_PRIVACY_URL`, `PIYOKEY_SUPPORT_URL` | 기본 typee.app 경로 |
| `PIYOKEY_POSTHOG_PROJECT_TOKEN` (+ `PIYOKEY_POSTHOG_HOST=https://eu.i.posthog.com`) | 분석 |
| `android/app/google-services.json` (미추적) | Crashlytics |
| `PIYOKEY_CONTENT_RIGHTS_CONFIRMED=true` | gTTS 음원·콘텐츠 권리 gate (iOS와 공통) |
| `PIYOKEY_UPLOAD_STORE_FILE`, `_STORE_PASSWORD`, `_KEY_ALIAS`, `_KEY_PASSWORD` | 업로드 키 |
| `PIYOKEY_PLAY_GAMES_PROJECT_ID` + 21개 `PIYOKEY_PLAY_GAMES_*_ID` | 리더보드 16(클래식 15 + 주간 피요컵) · 업적 5 |
| `PIYOKEY_CATALOG_URL` (선택) | 비우면 번들 카탈로그만 사용 |

```bash
./gradlew --no-configuration-cache :app:bundleDistributionRelease
```

## 3. 외부 gate (사용자/운영자)

- [ ] applicationId 확정과 Play Console 앱 생성 (카테고리 Education, 광고 없음, 무료)
- [ ] Play App Signing + 업로드 키 등록
- [ ] 인앱 상품 `app.piyokey.deckmaker.lifetime` (일회성, 이름 "typee pro"/"ピヨキー プロ") 생성·활성
- [ ] Play Games Services 프로젝트: 리더보드 16개(iOS ID와 1:1, `app/build.gradle.kts`의 `playGamesKeys`) · 업적 5개 생성 후 ID 주입
- [ ] Data safety: 선택 동의 시 익명 사용 분석(PostHog EU)·크래시 진단(Crashlytics) 수집, 기본 OFF, 광고 ID 미사용, 계정 없음, 사용자 덱은 기기 로컬
- [ ] 콘텐츠 등급 설문, 타깃 연령(13+), 앱 액세스(로그인 없음)
- [ ] 스토어 등록정보: `release/google_play/listing.json` 검수 후 입력(en-US·ja-JP·ko-KR, es/de/fr는 추가 번역 필요), 512 아이콘(`shared/brand` 원본에서 생성), 1024×500 그래픽
- [ ] 스크린샷: `release/google_play/screenshots/phone-en-US/`는 에뮬레이터 디버그 캡처 초안이다. 서명 후보에서 로케일별로 다시 캡처
- [ ] 콘텐츠·고정 발음 권리 확인 (`content_rights_confirmed`, iOS와 동일 gate)
- [ ] 내부 테스트 트랙 → 라이선스 테스터로 결제(구매·보류·환불·복원) 확인

## 4. 실기기 QA (동일 서명 후보, 최소 Galaxy 1 + Pixel 1)

- [ ] 입력 지연 p95 ≤ 50ms (debug `InputLatencyMonitor`), 2-pointer rollover 누락·중복 0
- [ ] 60초 흐름·산성비 60fps (debug FPS 배지), ANR/crash 0
- [ ] 삼성 키보드·Gboard 한국어(두벌식·천지인)로 OS 키보드 모드 조합·오타 판정
- [ ] 다른 앱 음악 재생 중 효과음·발음 공존, 백그라운드 전환 시 타이머·발음 정지
- [ ] 알림 권한 허용/거부, 매일 20:00 알림 실제 수신·시간대 변경 후 유지
- [ ] `.typedeck` 가져오기: 파일 앱·Google Drive·메일 첨부·공유 시트, 내보내기
- [ ] 태블릿 가로·세로 전환 시 세션 상태 유지
- [ ] 분석 OFF 상태에서 네트워크 전송 없음, ON 후 이벤트·금지 속성 미전송

## 5. 알려진 한계

- Play Games 주간 경계는 JST 월요일과 다르다(계획 §5.1-3). 로컬 주간 기록이 기준.
- 오프라인 한국어 TTS 음성이 없는 기기에서는 번들 MP3가 없는 동적 문구가 무음이다.
- 디버그 빌드는 인터프리터 실행이라 시작이 느리다(약 6초). release 빌드 에뮬레이터 콜드 스타트 1.2–1.9초.
