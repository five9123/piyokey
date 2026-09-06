# PIYOKEY Mac 로컬 데모

Issue #175의 `Piyokey Mac`은 macOS 14+ Mac Catalyst Debug 데모다. iOS 앱과 같은
`app.piyokey.Piyokey` bundle ID, 공식 콘텐츠, HangulEngine, 로컬 저장과 오프라인
음원을 재사용한다. 사용자 덱·Deck Maker·가져오기/내보내기·구매·피요컵·Game
Center 제출·CloudKit/KVS·배포는 포함하지 않는다.

## 빌드와 실행

```bash
xcodebuild build \
  -project ios/Hanco/Hanco.xcodeproj \
  -scheme 'Piyokey Mac' \
  -configuration Debug \
  -destination 'platform=macOS,variant=Mac Catalyst' \
  -derivedDataPath /private/tmp/hanco-175-derived
```

앱은 다음 경로에 생성된다.

```text
/private/tmp/hanco-175-derived/Build/Products/Debug-maccatalyst/Piyokey Mac.app
```

Finder에서 앱을 열거나 Xcode에서 `Piyokey Mac` scheme의 My Mac destination을 Run한다.
데모 전에 macOS 입력 메뉴에 한국어 두벌식을 추가하고 한국어 입력으로 전환한다.

## 5분 데모 순서

1. 커리큘럼 탭에서 첫 스테이지를 열고 물리 한국어 키보드로 목표를 입력한다. 발음
   버튼, Backspace, 한/영 전환 뒤 재포커스를 확인하고 결과까지 완료한다.
2. 공식 덱 탭에서 번들 공식 덱을 설치하고 Play를 눌러 같은 IME 입력과 결과 저장을
   확인한다.
3. 게임 탭에서 흐름, 산성비, 초성 맞추기, 받아쓰기, 단어 맞추기를 차례로 시작해
   정답 입력과 종료 결과를 확인한다.
4. 띄어쓰기를 열어 좌우 방향키로 경계를 이동하고 Space로 공백을 토글한 뒤 결과를
   확인한다.
5. 세션 중 창 크기를 바꾸고 앱을 비활성화했다가 복귀한다. 타이머·발음 정지를
   확인한 뒤 앱을 종료·재실행해 완료 기록과 최고 기록이 남아 있는지 확인한다.

## 검증 기록 원칙

- 자동 빌드/테스트, 실제 Mac에서 관찰한 수동 항목, 아직 실행하지 못한 항목을
  구분한다.
- Simulator 또는 상위 Mac의 화면 확인을 iPhone 12 60fps 실기기 gate나 Mac 배포
  승인으로 기록하지 않는다.
- 배포용 archive, notarization, App Store Connect, Universal Purchase 연결과
  CloudKit/KVS 동기화는 별도 승인과 후속 Issue가 필요하다.

## 후속 동기화 경계와 마이그레이션

이번 데모는 기존 로컬 store를 그대로 사용하며 동기화 코드를 추가하지 않는다. 설정처럼
작고 마지막 값이 중요한 데이터는 `NSUbiquitousKeyValueStore` adapter 후보로 두고,
커리큘럼 진행·복습·게임 기록은 CloudKit private database의 `CKSyncEngine` adapter
후보로 둔다. UI와 ViewModel은 현재 store API만 호출하고, 후속 adapter가 로컬 파일과
CloudKit 사이의 변환·전송을 맡는 경계를 유지한다. 사용자 덱은 Mac 데모 범위 밖이므로
후속 동기화 대상에도 자동으로 포함하지 않는다.

후속 승인을 받으면 별도 PRD/Issue에서 다음 순서로 진행한다.

1. 현재 JSON/UserDefaults schema와 최대 2,048개 기록 제한을 버전이 있는 동기화
   record로 명세하고 stable ID, tombstone, 충돌 정책을 먼저 확정한다.
2. 기존 로컬 데이터를 읽기 전용으로 스캔해 CloudKit record로 변환하는 idempotent
   migration을 만든다. 업로드가 끝나기 전까지 로컬 파일을 삭제하거나 재작성하지 않는다.
3. 내부 opt-in Debug build에서 단일 기기 업로드, 두 기기 병합, 오프라인 재시도,
   계정 로그아웃·재로그인과 downgrade 복구를 검증한다.
4. migration 성공 표식을 기록한 뒤 dual-read 기간을 운영하고, 원격 오류에서는 로컬
   store를 계속 단일 진실 원본으로 사용한다. 원격 데이터 삭제와 배포 전환은 별도 gate로
   남긴다.

CloudKit container, entitlement, production schema, 계정 UI, 서버 환경과 데이터 삭제
정책은 이 구현에 없다. KVS와 CKSyncEngine 선택도 후속 설계 검토 전에는 확정된 배포
계약이 아니다.

## 2026-09-07 구현 인계

- 자동 확인: `/opt/homebrew/bin/python3.12 tools/workspace_doctor.py --strict`, repository
  preflight, preflight 단위 테스트 17개, signed Mac Catalyst Debug build가 통과했다.
- iOS 회귀: iPhone 17 / iOS 26.5에서 Practice, OS IME, 5개 직접 입력 게임,
  띄어쓰기, 커리큘럼/게임 저장, 오디오 focused selector 200개가 통과했다.
- Mac 수동 확인: 첫 커리큘럼 스테이지를 물리 한국어 입력 10개, 실수 0개로 완료했고
  종료·재실행 뒤 다음 스테이지 해금이 유지됐다. 공식 번들 카탈로그에서 공식 덱만
  표시되는 것과 Everyday Korean Words 설치 뒤 Play 버튼이 활성화되는 것을 확인했다.
  흐름은 목숨 소진
  결과, 산성비는 물리 입력 8개·정확도 100% 결과를 확인했으며 두 화면과 결과에
  Game Center 동작이 없었다. 초성 맞추기는 Backspace 조합 복구를 포함해 10/10,
  100% 결과를 확인했다. 단어 맞추기는 겹모음·`ㄶ` 받침을 포함해 10/10, 100%
  결과를 확인했고, Control-Space로 영문 전환했을 때 안내가 나타나며 한국어 복귀 뒤
  입력이 이어지는 것도 확인했다. 띄어쓰기는 좌우 방향키와 Space로 81개 경계를 모두
  이동·수정하고 1,000점·100% 결과를 확인했다.
- 수동 확인 대기: 받아쓰기의 전체 자연 종료 결과, 공식 덱 연습 완료,
  수정된 흐름/산성비 HUD, 최소 창 resize의 CTA, inactive/active 타이머·발음 정지,
  효과음·발음 청취는 아직 최종 build에서 관찰하지 못했다.
  이 항목은 자동 build 통과와 구분해 OPEN으로 유지한다.
