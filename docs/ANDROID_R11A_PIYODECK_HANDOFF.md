# Android R1.1A `.piyodeck` handoff

기준일: 2026-08-25 (JST)

## 범위

이 증분은 앱 1.1의 무료 사용자 덱 문서 수명주기만 구현한다. Android Storage
Access Framework picker, 파일 앱의 `VIEW`, 공유 시트의 `SEND`가 같은 제한 복사와
strict parser를 사용한다. 선택만으로 설치하지 않고 localized metadata와 첫 3항목,
현재본 충돌을 확인한 뒤 사용자가 설치 또는 명시적 교체를 선택한다.

가져오기·연습/호환 게임·현재본 내보내기·삭제·내보낸 뒤 삭제·재가져오기는
entitlement 없이 동작한다. 새 덱·편집·공식 덱 사본은 잠긴 상태로 남으며 다음
Deck Maker/Play Billing 증분에서 `app.piyokey.deckmaker.lifetime`에 연결한다.

## 저장·복구 계약

- 원본 `content://`는 복사 시간에만 열고 최대 8 MiB를 넘기기 전에 실패한다.
- app-private staging package와 최소 sidecar는 process recreation을 견디고 24시간 뒤
  정리된다. 다른 새 import가 오면 이전 pending은 명시적으로 폐기한다.
- strict reader가 ZIP 구조, CRC/SHA, UTF-8/JSON/schema/DeckKit, user ID와 항목 제한을
  모두 통과한 뒤에만 repository preview가 생성된다.
- Room v3→v4 `user_deck_history`는 payload와 분리해 import/delete/version/SHA/last-played
  이력을 보존한다. 게임·복습 row는 덱 삭제 때 제거하지 않는다.
- install/replace/recovery는 journal, 같은 디렉터리 atomic replace, Room transaction
  순서다. 동일 SHA는 write 없이 성공하고 TOCTOU를 막기 위해 commit 직전 staging을
  다시 검증한다.

## 자동 증거

- `core:piyodeck`: 공용 정상·악성 fixture와 canonical writer 계약.
- `core:platform`: 제한 stream copy, pending newest recovery·sidecar 폐기.
- `core:data`: 신규/동일/업데이트 conflict, 명시적 replace, export round trip,
  delete/reimport last-played 복원, 악성 SHA 무변경, Room v3→v4 migration.
- API 35 app UI: 연습 중 외부 문서를 staging하되 import UI를 숨기고, 세션 종료 뒤
  미리보기와 무료 설치를 완료한다. 거부된 손상 문서는 오류 표시 뒤 staging package와
  pending sidecar를 즉시 제거한다.
- ja/en/ko 리소스, manifest `VIEW`/`SEND` custom MIME와 `singleTop`, debug/androidTest APK,
  lint와 Debug·Release build를 Source CI에서 검증한다.

최종 로컬 결과는 API 35 `core:data` 16개와 앱 전체 23개, 자동 pointer 4개 통과다.
pointer의 실기기 전용 2개는 Issue #19로 이관돼 계획대로 skip됐다.

## 후속 경계

Deck Maker 초안/편집/공식 사본과 Play Billing, Play Games, 최종 API/서명/Play Console
준비는 별도 Issue다. Files·타 앱 공유·OEM URI provider 상호운용은 Galaxy IME·오디오·
성능·launcher와 함께 Issue #19 출시 후보 통합 실기기 QA에서 한 번만 수행한다.
