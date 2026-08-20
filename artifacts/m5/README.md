# M5 커리큘럼 + 리텐션 검증 산출물

## 환경
- iPhone 17 Simulator / iOS 26.5
- 일본어 UI (`ja_JP`)
- 데일리 회귀 기준일: 2026-07-19 JST (`UITEST_JST_DAY`)

## 기준 이미지
- `curriculum-retention-home-ja.png`: 6챕터 진행 헤더, 7일 스탬프 카드, 데일리 챌린지, 기본 OFF 리마인더와 시간 설정
- `daily-challenge-complete-ja.png`: 5단어 완료 후 오늘 스탬프·1일 스트릭·완료 배지
- `practice-result-review-ja.png`: 자동 수집된 오답, 자모가 속한 음절 강조, 1탭 복습 CTA

## 성공 흐름 영상
- `m5-daily-success-flow-ja.mp4`
- H.264, iPhone 17 세로 화면
- 흐름: 리마인더 기본 OFF 확인 → 2026-07-19 로컬 시드 5단어 데일리 챌린지 → 완료 → 오늘 스탬프와 1일 스트릭 확인
- 동일 흐름은 `HancoUITests.testDailyChallengeCompletesTodaysStampAndReminderDefaultsOff`로 재현한다.

## 종료 검증
- iOS 단위·UI 테스트: 95/95 통과, 실패·스킵 0
- SwiftPM `HangulEngine`·`DeckKit`: 13/13 통과
- generic iOS Release 빌드: 통과 (`CODE_SIGNING_ALLOWED=NO`)
- JST 자정 직전/직후, 자정을 넘긴 세션의 시작일 귀속, 스트릭 연속·공백, 일자별 결정적 챌린지, 리마인더 기본 OFF·허용 거부·재예약·취소를 단위 테스트로 고정한다.
