# Android M7 M6D Play Games handoff

## 범위

Issue #36은 PRD F6f의 Android 선택형 Play Games v2 계약을 구현한다. 로컬 `GameRecord`, `DeckProgress`, 학습·스트릭·성장 상태가 항상 원본이고 Google 서비스는 결과 화면에서만 접근 가능한 비차단 mirror다.

## 구현 계약

- `core:game/PlayGamesPolicy.kt`: 5모드 × 3단계 정확한 번들 ID만 15개 논리 보드에 매핑한다. 내장 키보드가 아니거나 주간컵이면 null이다.
- Room v6: 최고점 outbox, achievement current/synced progress, lifetime accepted jamo, idempotent practice event를 추가한다.
- `core:platform/PlayGamesIntegration.kt`: 완전한 외부 설정에서만 v2 SDK를 활성화하고 silent auth check, 명시적 sign-in, 최고점·업적 retry, 기존 서버 점수 확인을 제공한다.
- 결과 화면: 자격 있고 설정된 빌드에만 `Play Games 랭킹` 버튼을 표시한다. 주간컵·OS IME·사용자 덱·띄어쓰기는 표시하지 않는다.
- champion trophy: 첫 제출 성공 또는 서버 점수 확인 뒤 영구 해금한다.

외부 ID 이름은 `android/release/play_games.properties.example`이 고정한다. 배포 파이프라인은 `:app:verifyPlayGamesConfiguration`을 실제 private properties와 함께 실행해야 한다.

## 자동 검증

- Android Source CI 동일 명령: 949 tasks 성공, JVM·lint·Debug/Release APK 포함.
- API 35 `emulator-5554`: data 24/24, 앱 전체 30/30. 앱 전체에는 Play Games 결과/주간컵 UI 4개와 기존 발견·학습·게임·설정·Deck Maker 회귀가 포함된다.
- Python tools 56/56, release preflight 통과.
- SwiftPM 42/42.
- 완전한 21개 더미 resource ID로 configuration-cache 호환 `:app:verifyPlayGamesConfiguration` 통과.

연결된 Galaxy는 사용하지 않았다.

## 출시 전 외부 gate

Issue #19에서 최종 application ID와 서명 identity가 확정된 뒤 Play Console project/OAuth, 15개 leaderboard, 5개 achievement를 만들고 실제 ID를 주입한다. license tester로 명시적 로그인, 각 모드 1개 제출, 재실행 outbox backfill, 랭킹 UI, 업적, 기존 서버 점수 기반 trophy를 확인한다. 이 검증 전에는 Play Games 운영 준비 완료로 판단하지 않는다.
