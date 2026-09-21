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

1. **0:00–1:20** 커리큘럼의 한 스테이지에서 물리 한국어 키 몇 개, Backspace,
   발음과 다음 스테이지 해금을 대표로 보여 준다.
2. **1:20–2:00** 공식 덱 탭이 공식 콘텐츠만 표시하는 것을 보여 주고 덱 하나를
   설치해 Play로 들어간 뒤 항목 한두 개를 입력한다.
3. **2:00–3:25** 흐름과 초성 맞추기 또는 단어 맞추기 중 하나를 실제로 입력한다.
   다른 게임은 선택 화면과 시작 화면을 열어 제공 범위를 보여 준다.
4. **3:25–4:10** 받아쓰기에서 발음을 재생하고 항목 하나를 입력한다. **4:10–4:45**
   띄어쓰기에서 좌우 방향키와 Space 동작을 몇 경계만 대표로 보여 준다.
5. **4:45–5:00** 안정적인 결과 화면에서 창 resize와 저장 기록을 보여 준다. 아래
   전체 검증 기록은 사전 QA 결과이며 5분 안에 모든 세션을 완주했다는 뜻이 아니다.

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

## 2026-09-07 구현 인계와 수동 검증

- 자동 확인: 구현 SHA `6ea9aea880c0b48aee93191f9c21b05185cddd20`에서
  `/opt/homebrew/bin/python3.12 tools/workspace_doctor.py --strict`, repository preflight,
  preflight 단위 테스트 17개와 signed Mac Catalyst Debug build가 통과했다. 이후 IME
  lifecycle/retry와 문서까지 포함한 SHA `c7849b17c0a6683fd15d7c10072fd13fad7f893b`에서는
  strict doctor, preflight 17개, repository preflight와 signed Mac build를 다시 통과했다.
- iOS 회귀: 초기 구현 SHA `6ea9aea`에서 Practice, OS IME, 5개 직접 입력 게임,
  띄어쓰기, 커리큘럼/게임 저장, 오디오 focused selector 208개가 통과했다. 이후 최종
  IME 변경을 포함한 `c7849b1`에서 OS IME focused unit test 69개와 Flow 결과의 Retry 뒤
  OS IME로 목표를 입력해 점수가 증가하는 focused UI test 1개를 통과했다.
- Mac 수동 확인: 첫 커리큘럼 스테이지를 물리 한국어 입력 10개, 실수 0개로 완료했고
  종료·재실행 뒤 다음 스테이지 해금과 설치한 공식 덱이 유지됐다. 공식 번들 카탈로그는
  공식 덱만 표시했고 Everyday Korean Words를 설치한 뒤 공식 연습을 물리 입력 12/12,
  정확도 100%, 실수 0개, 별 3개로 완료했다. 약 813×648 창에서도 덱 Play CTA와
  받아쓰기 입력 footer가 잘리지 않았다.
- 게임 전체 확인: 흐름은 HUD가 countdown 뒤에도 유지됐고 물리 입력 8개·452점 및
  결과 Retry 뒤 `회사` 입력·50점 증가를 확인했다. 산성비는 물리 입력 8개·458점·정확도
  100%·combo 8, 초성 맞추기는 Backspace 조합 복구를 포함해 10/10·1,607점·100%,
  단어 맞추기는 겹모음과 `ㄶ` 받침을 포함해 10/10·1,571점·100%를 확인했다.
  받아쓰기는 공식 덱 10/10·1,450점·91.2%·combo 9로 완료했고 Listen again 동작에
  오류가 없었다. 띄어쓰기는 좌우 방향키와 Space로 81개 경계를 모두 이동·수정해
  1,000점·100% 결과를 확인했다. 해당 결과 화면에는 Game Center 동작이 없었다.
- lifecycle·오디오 확인: 흐름과 띄어쓰기에서 inactive 동안 timer·목표·목숨/위치가
  유지되고 복귀 뒤 timer가 wall-clock 시간을 따라잡아 감소하지 않았다. 사용자가
  Cmd-H 뒤 Dock으로 복귀해 물리 한국어 입력이 계속 전달되는 것을 확인했다. 발음과
  효과음이 실제로 들리고 재생 중 Cmd-H에서 발음이 정지하는 것도 사용자가 확인했다.
  CUA의 Cmd-H/Raise 경로에서만 합성 키가 UIKit `editingChanged`에 도달하지 않았으므로
  이 현상은 데모 앱의 사람 입력 실패로 재현되지 않았으며 자동화 integration의 세부
  원인은 더 좁히지 않았다.
- HUD 확인: 최종 exact app의 산성비에서 countdown 뒤 52초 시점에도 timer, score,
  combo, lives 네 항목이 모두 표시됐다. 흐름 HUD 확인과 함께 수정 HUD gate를 통과했다.
- Mac 로컬 데모의 사람 확인 gate는 통과했다. iPhone 12 60fps 실기기, 콘텐츠 권리,
  계정·IAP·스토어·release signing·배포는 이 데모 밖의 release gate로 계속 OPEN이다.
