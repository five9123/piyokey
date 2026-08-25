# Android M7 M6C handoff

기준일: 2026-08-25 (JST)

## 범위

M6C는 PRD F8~F12와 §7의 Android 폴리싱을 포팅한다. 효과음은 첫 입력에서만
48kHz mono fallback WAV를 만들고 지연식 `SoundPool`로 재생하며 15초 유휴 또는
백그라운드에서 반납한다. 다른 앱 음악을 멈추거나 duck하는 audio-focus gain은
요청하지 않는다. 발음은 선언 MP3→canonical MP3 순서이고 앱 제공 고정 문구의
누락은 출시 계약 실패로 취급한다. 번들에 없는 사용자·동적 문구만 네트워크가
필요 없는 ko-KR 기기 음성으로 폴백한다.

게임·연습 결과는 앱 언어 브랜드, 모드/덱, 점수, 콤보, 스트릭을 포함한 정확히
1,200×1,200 PNG를 로컬에서 만든다. 저장은 API 29+ MediaStore scoped storage와
API 26~28의 버튼 시점 권한을 사용하고, 공유는 좁게 제한한 FileProvider의
`content://` URI만 전달한다.

성장 피요는 Compose Canvas 공용 컴포넌트로 온보딩·홈·연습·게임·마이페이지에
연결했다. 3/5/7일 소품과 TOPIK 게임 참여 안경은 영구·단조 해금이고, `자동`은
성장 기본 모습만 사용한다. 선택 외형은 세션 시작 값으로 유지한다. 옷장에는
최종 모습 미리보기와 자동/없음/해금 소품만 노출한다. iOS 공용 1024 RGB 브랜드
원본을 Android 빌드가 생성 리소스로 복사해 launcher/adaptive icon과 공유 카드에
같이 사용한다.

## 자동 증거

- `core:settings`: 성장/옷장 단조 해금, 자동/없음, 세션 스냅샷.
- `core:platform`: 발음 경로와 고정/동적 폴백, 48kHz 짧은 WAV 계약.
- Android CI 동등 946개 Gradle 작업: 순수/JVM 계약, feature/app lint, 계측 APK,
  Debug·Release APK.
- API 35 앱 계측 21개와 연습 계측 6개: M3~M6 전체 흐름, 1,200 PNG,
  FileProvider URI, 옷장 영속/진입, canonical MP3. 물리 전용 2개는 의도적으로 skip.
- Python 도구/프리플라이트 56개와 SwiftPM 42개, iOS generic Simulator 빌드.

## 후속 경계

Play Games Services, `.piyodeck` SAF 가져오기/내보내기, Deck Maker와 Play Billing은
별도 Issue다. 실제 Galaxy 외부 음악 혼합·Bluetooth/통화 인터럽트·IME·60fps와
터치 정량 gate는 Issue #19의 출시 후보 통합 QA에서 한 번만 실행한다.
