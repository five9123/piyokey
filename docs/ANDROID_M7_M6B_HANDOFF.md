# Android M7 M6B handoff

기준일: 2026-08-25 (JST)

## 범위

M6B는 PRD F6~F6e의 Android 게임 포팅을 완성한다. 게임 허브의 여섯 카드는
`흐름·산성비 / 초성·받아쓰기 / 단어 맞추기·띄어쓰기` 순서이며 모두 네트워크
없이 선택→플레이→결과→복습 확인→재도전으로 이어진다. 다섯 타이핑 게임은 모드별
100단어 코스 3개와 덱 찾기 카드를 2×2로 제공하고 설치 덱은 아래에 분리한다.

산성비는 3레인에서 최대 4장을 독립 이동하고 약 42% 간격 생성, 3목숨, 1.5배
점진 가속을 적용한다. 초성·단어 맞추기·받아쓰기는 최대 10개 고유 표본, 자모 판정,
자동 전환, 오타 복습과 내장/OS IME를 공유한다. 받아쓰기는 완료 전 시각·접근성
트리에 정답 단서를 넣지 않고 300개 canonical MP3를 자동/수동 재생한다.

띄어쓰기는 자체 작성 100~200자 글 6개에서 비공백 원문 사이 경계만 편집한다.
최초 판단, 수정, 최종 경계 집합을 분리하고 결과에 첫 정확도·최종 완성도·수정 횟수와
최대 8개 오답 문맥을 표시한다.

## 자동 증거

- `core:game` JVM reducer tests: 점수, 시간, pause/resume, 결정적 표본, 동일 초성,
  정답 비노출, 다중 카드 targeting, 경계 집합 검증.
- API 35 app instrumented tests 7개: 다섯 게임 오프라인 진입 5개와 흐름·직접 입력
  OS IME 2개.
- API 35 content test: 게임 프리셋 15개×100항목, 받아쓰기 MP3 300개, 띄어쓰기
  6개 길이·순서 계약.
- Android feature/app lint, Debug·Release APK, Python release preflight, SwiftPM shared
  tests와 generic iOS simulator build를 PR gate로 사용한다.

## 후속 경계

M6B는 공유 이미지, 효과음·햅틱·오디오 설정의 전체 연결, 성장 피요·옷장,
Play Games, `.piyodeck` Android import/export·Deck Maker·Billing을 완료로 주장하지
않는다. 실제 Galaxy의 삼성/Google IME, 외부 음악 혼합 청취, 60fps와 터치 정량
gate도 Issue #19에 남아 있으며 앱 전체 출시 후보가 된 뒤 한 번에 실행한다.
