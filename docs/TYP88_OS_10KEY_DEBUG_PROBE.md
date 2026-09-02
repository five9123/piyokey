# TYP-88 OS 한국어 10키 Debug probe

이 probe는 Apple 기기가 실제로 전달하는 `committed`·`marked` 중간 상태를
측정하기 위한 DEBUG 전용 화면이다. Release/TestFlight에서는 노출되지 않는다.
현재 설치된 TestFlight 1.1 (12)를 덮어쓰거나 재설치하지 않는다. 정민·니모가
별도로 기기 일정을 확정하고, Debug 앱 설치로 기존 TestFlight 앱이 교체되어도 되는
기기에서만 아래 절차를 수행한다.

## 실행

1. Xcode의 `Hanco` scheme Run configuration을 `Debug`로 두고 launch environment에
   `TYP88_IME_PROBE=1`만 추가한다. `TYP83_IME_PROBE`는 설정하지 않는다.
2. 예약된 iPhone 또는 iPad를 선택해 앱을 실행하고 iOS 입력 소스를 한국어 10키로
   전환한다. 붙여넣기나 내장 키보드는 사용하지 않는다.
3. 화면 순서대로 각 목표를 정확히 입력한다. probe는 완성된 목표만 다음 항목으로
   넘기며, 잘못 입력했으면 OS 키보드 Backspace로 현재 목표에서 다시 시도한다.
4. Xcode console에서 `TYP-88 row 13`을 필터링하고 시작부터 `Capture complete`까지
   원문을 보존한다. 각 목표 header의 기기·OS와 모든 step의 Unicode scalar,
   `committed`, `marked`를 생략하지 않는다.

자동 corpus 순서는 다음과 같다.

```text
일해 · 말해 · 말했다 · 급해 · 입학 · 번째 · 각하
읽어 · 닭 · 삶 · 많이 · 앓다 · 읊다
앉아 · 없다 · 못해 · 위키백과 · 돼 · 과 · 웨 · 의
```

## 증빙 판독·보관

- Class A에서는 닫힌 앞 음절 뒤 다음 초성을 누르는 동안 원래 받침과 초성 recipe
  prefix가 병합되는지, 그 상태가 committed인지 marked인지 그대로 기록한다.
- Class B에서는 목표 겹받침의 첫째·둘째 성분 조립과 뒤 모음 입력 시 재분리되는 모든
  step을 보존한다. 특히 `삶`·`많이`에서 `살ㅇ`·`만ㅅ` 같은 dangling 표기가 실제로
  관찰되는지 명시한다.
- 관찰되지 않은 dangling 형태를 추론해 판정 예외로 추가하지 않는다. 관찰됐다면 해당
  원문 step과 기기·OS를 TYP-81에 첨부한 뒤 별도 구현 판단을 받는다.
- iPhone과 iPad 로그는 구분해 TYP-81에 첨부한다. Debug probe 결과는 정확한 build 13
  TestFlight의 전체 corpus 실기기 통과를 대신하지 않는다.

