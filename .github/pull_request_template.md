## 목적

<!-- 사용자/유지보수 관점에서 무엇이 달라지는지 적어 주세요. -->

Closes #

## 계약과 범위

- 관련 PRD 절:
- Acceptance criteria:
- 영향 플랫폼: iOS / Android / Web / Shared / Tools
- 변경한 공용 schema·fixture·vector:

## 검증

- [ ] 관련 unit/contract test
- [ ] 관련 UI test 또는 수동 시나리오
- [ ] `python3 tools/release_preflight.py` (콘텐츠·릴리스 계약 영향 시)
- [ ] 공용 계약을 소비하는 모든 구현의 test

실행한 명령과 결과:

```text

```

미실행 test/gate와 이유:

```text

```

## 안전 점검

- [ ] 사용자 노출 문자열을 localization resource에 추가했다.
- [ ] 세션 중 modal/interruption을 추가하지 않았다.
- [ ] secret, signing asset, generated release artifact를 포함하지 않았다.
- [ ] PRD 밖 결정을 `DECISIONS.md`에 기록했거나 해당 사항이 없다.
- [ ] 스크린샷/영상이 필요한 UI 변경이면 전후 자료를 첨부했다.
