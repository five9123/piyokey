# Android M7 M6A handoff

기준일: 2026-08-25 (JST)

## 범위

M6A는 PRD F1, F2a, F10의 Android 기반을 포팅한다. 새 사용자는 목표·신뢰 안내,
두벌식 소개, 네 번째 탭 이내 실제 `ㄱ` 입력과 첫 `가` 완성을 거쳐 부화 미션으로
연결된다. 소개 스킵은 설명과 첫 입력만 건너뛰며 챕터1~3 게이트는 건너뛰지 않는다.
기존 설치 사용자는 마이그레이션으로 이 게이트를 다시 보지 않는다.

Preferences DataStore는 ja/en/ko, 테마, 글자 크기, 효과음·타건음·햅틱, 로마자·키
가이드, 기본 입력 방식, 연습 표시 프리셋·필드·순서, 자동 발음, 초성 뜻 표시와
온보딩 진행을 저장한다. 설정은 모든 탭의 공통 우측 상단 시트에서 즉시 반영한다.

연습 OS IME는 표준 `EditText`의 composing 범위와 committed text를 분리해 순수
`OSIMETextJudge`에 전달한다. 조합 중 불일치는 오타로 세지 않고 확정 불일치만
집계한다. 챕터1~4와 부화 미션은 내장 키보드 고정이며 챕터5+, 자유 연습, 설치 덱,
데일리와 복습은 세션 상태를 보존한 채 입력 방식을 바꿀 수 있다. 한국어 IME가
없어도 세션은 차단하지 않고 사용자가 누른 경우에만 시스템 키보드 설정을 연다.

## 자동 증거

- 관련 JVM tests 41개: settings 4, session 16, retention 10, practice 11.
- API 35 app instrumented tests 10개: M3~M5 회귀 6, M6 온보딩 2, 설정 2.
- M6 온보딩은 일반 경로의 네 번째 탭 실제 입력과 스킵 후 부화 gate 유지를 분리 검증한다.
- `:app:lintDebug`, practice/onboarding/settings lint, Debug·Release APK 빌드 통과.
- 실기기 입력 지연·물리 rollover·IME 종류·외부 음악·알림은 Issue #19 출시 후보 통합 QA에 유지한다.

## 후속 경계

M6A는 나머지 게임 모드, 오프라인 고정 발음·효과음, 공유 이미지, 피요 성장·옷장,
Play Games, `.piyodeck`/Deck Maker·Billing을 완료로 주장하지 않는다. 이 기능은
후속 Issue와 branch에서 PRD 순서대로 구현하고, 앱 전체 자동 출시 검증 뒤 Galaxy
통합 QA로 넘긴다.
