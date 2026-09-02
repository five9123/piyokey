# TYP-83 IMETextField row-13 실기기 probe

이 probe는 TYP-83 AC 1의 iPhone·iPad OS 천지인 `committed`/`marked`
전이를 기록한다. Simulator 입력, recipe 추론, `setMarkedText` 테스트는 이 실측을
대체하지 않는다. iPhone 측정은 2026-09-02에 완료했으며 원문은
`docs/TYP-83_IPHONE_IME_PROBE_2026-09-02.log`에 보존했다. iPad 측정과
TestFlight 실기기 gate는 **OPEN**이며, 두 물리 기기의 로그를 TYP-83에 첨부하기
전까지 AC 1 전체를 통과로 기록하지 않는다.

## 준비

1. Xcode에서 Hanco Debug 실행의 환경 변수 `TYP83_IME_PROBE=1`을 설정한다.
   이 값이 있는 Debug 빌드는 일반 제품 화면 대신 전용 TYP-83 probe 화면으로
   바로 시작한다. Release 빌드에는 이 launch surface가 포함되지 않는다.
2. 실제 iPhone 또는 iPad에 Debug 앱을 설치하고 기기 설정에서 한국어
   `10키` 키보드를 선택한다.
3. probe 화면의 실제 `IMETextField`가 자동으로 focus되어 OS 키보드가 열린다.
   내장 10키는 측정 대상이 아니다.
4. Xcode Console에서 category `TYP-83 IMETextField row 13` 또는 문자열
   `TYP-83 row 13`으로 필터링한다.

## 캡처

각 기기에서 화면에 제시되는 `돼`, `과`, `웨`, `의`를 순서대로 완성한다. 일치하면
probe가 자동으로 다음 target의 빈 필드로 advance/reset한다. 매 키/플릭 뒤 나타난
모든 로그를 생략하지 않고 복사한다. 각 묶음에는
기기 이름, iOS/iPadOS 버전, `Mode: IMETextField`, 현재 target과 순번이 자동으로
포함된다.

```text
Device: <physical device name> [iPhone|iPad]
OS: iOS|iPadOS <version>
Mode: IMETextField
Target: 돼
1 committed=U+... "..." marked=∅
2 committed=U+... "..." marked=U+... "..."
...
```

동일 절차를 실제 iPhone 1대와 실제 iPad 1대에서 각각 수행한다. 앱의 판정이나
필드 복구가 문서를 바꾸더라도 그 직전 `IMETextField.textDidChange`에서 기록된
줄을 포함한다. 결과에는 사용한 키보드가 OS 한국어 10키임을 명시하고 원문 로그를
TYP-83에 첨부한다.

## 종료 조건

- iPhone과 iPad 각각에 `돼`, `과`, `웨`, `의`의 단계별 원문 로그가 있다.
- 모든 묶음에 실제 기기명과 OS 버전이 있다.
- 로그가 TYP-83에 첨부돼 reviewer가 committed/marked 경계를 확인할 수 있다.

위 세 조건이 모두 충족되기 전에는 AC 1을 통과로 기록하거나 물리 기기 gate를
닫지 않는다.

현재 상태: iPhone 15 Pro JM / iOS 26.6.1의 네 target 원문 로그는 완료했다.
iPadOS 실기기 로그는 아직 없으므로 AC 1은 **OPEN**이다.
